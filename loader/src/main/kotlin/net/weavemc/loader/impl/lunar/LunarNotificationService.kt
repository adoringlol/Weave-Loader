package net.weavemc.loader.impl.lunar

import me.xtrm.klog.dsl.klog
import net.weavemc.api.lunar.LunarNotificationProvider
import net.weavemc.api.lunar.LunarNotificationStatus
import java.lang.instrument.Instrumentation
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Obfuscation-resistant access to Lunar Client's native notification manager.
 *
 * Lunar changes implementation names between releases, so discovery relies on the
 * stable shape of the notification manager and its singleton access chain. All
 * expensive reflection is lazy, rate-limited, and cached after the first success.
 */
internal class LunarNotificationService(
    private val instrumentation: Instrumentation,
) : LunarNotificationProvider {
    private val logger by klog

    private val persistentCacheEnabled: Boolean =
        System.getProperty("weave.lunar.cache")?.toBooleanStrictOrNull()
            ?: System.getProperty("agentloader.lunar.cache")?.toBooleanStrictOrNull()
            ?: true

    @Volatile
    private var notificationMethod: Method? = null

    @Volatile
    private var notificationManager: Any? = null

    @Volatile
    private var lastDiscoveryAttemptNanos: Long = Long.MIN_VALUE

    override val status: LunarNotificationStatus
        get() = if (notificationMethod != null && notificationManager != null) {
            LunarNotificationStatus.READY
        } else {
            LunarNotificationStatus.NOT_READY
        }

    override fun send(title: String, message: String): Boolean {
        if (status != LunarNotificationStatus.READY && !discoverIfDue()) return false

        val method = notificationMethod ?: return false
        val manager = notificationManager ?: return false

        return runCatching {
            method.invoke(manager, title, message)
            true
        }.getOrElse {
            logger.warn("Lunar notification invocation failed; integration will be rediscovered", it)
            synchronized(this) {
                notificationMethod = null
                notificationManager = null
                lastDiscoveryAttemptNanos = Long.MIN_VALUE
            }
            false
        }
    }

    private fun discoverIfDue(): Boolean {
        val now = System.nanoTime()
        if (
            lastDiscoveryAttemptNanos != Long.MIN_VALUE &&
            now - lastDiscoveryAttemptNanos < DISCOVERY_RETRY_NANOS
        ) return false

        synchronized(this) {
            if (status == LunarNotificationStatus.READY) return true

            val lockedNow = System.nanoTime()
            if (
                lastDiscoveryAttemptNanos != Long.MIN_VALUE &&
                lockedNow - lastDiscoveryAttemptNanos < DISCOVERY_RETRY_NANOS
            ) return false
            lastDiscoveryAttemptNanos = lockedNow

            return discover()
        }
    }

    private fun discover(): Boolean {
        val gameLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader

        if (persistentCacheEnabled) {
            val started = System.nanoTime()
            val cached = LunarResolutionCache.load(gameLoader)
            if (cached != null && isNotificationMethod(cached.notificationMethod)) {
                cached.notificationMethod.isAccessible = true
                notificationMethod = cached.notificationMethod
                notificationManager = cached.manager
                logger.info("Lunar notification cache hit in ${(System.nanoTime() - started) / 1_000}µs")
                return true
            }
        }

        val started = System.nanoTime()
        val lunarClasses = instrumentation.allLoadedClasses.asSequence()
            .filter { it.name.startsWith(LUNAR_CLIENT_PACKAGE) }
            .toList()

        val managerClass = lunarClasses.firstOrNull(::isNotificationManager) ?: return false
        val method = managerClass.declaredMethods.firstOrNull(::isNotificationMethod) ?: return false
        val resolution = findManagerInstance(managerClass, lunarClasses) ?: return false

        return runCatching {
            method.isAccessible = true
            notificationMethod = method
            notificationManager = resolution.manager
            logger.info("Lunar native notification integration discovered in ${(System.nanoTime() - started) / 1_000_000}ms")
            if (persistentCacheEnabled) {
                LunarResolutionCache.save(
                    gameLoader,
                    LunarResolutionCache.Discovery(
                        manager = resolution.manager,
                        notificationMethod = method,
                        utilityClass = resolution.utilityClass,
                        staticGetter = resolution.staticGetter,
                        intermediateClass = resolution.intermediateClass,
                        managerGetter = resolution.managerGetter,
                    ),
                )
            }
            true
        }.getOrElse {
            logger.debug("Could not make Lunar's notification method accessible", it)
            false
        }
    }

    private fun isNotificationManager(candidate: Class<*>): Boolean = runCatching {
        !candidate.isInterface &&
                !candidate.isEnum &&
                candidate.declaredFields.any { it.type == ConcurrentLinkedQueue::class.java } &&
                candidate.declaredMethods.any(::isNotificationMethod)
    }.getOrDefault(false)

    private fun isNotificationMethod(method: Method): Boolean =
        method.parameterTypes.contentEquals(NOTIFICATION_PARAMETERS) &&
                method.returnType != Void.TYPE &&
                method.returnType != Boolean::class.javaPrimitiveType

    private fun findManagerInstance(managerClass: Class<*>, lunarClasses: List<Class<*>>): ManagerResolution? {
        for (utilityClass in lunarClasses) {
            if (!utilityClass.name.startsWith(LUNAR_UTIL_PACKAGE)) continue

            val staticGetters = runCatching { utilityClass.declaredMethods }.getOrNull() ?: continue
            for (staticGetter in staticGetters) {
                if (!Modifier.isStatic(staticGetter.modifiers) || staticGetter.parameterCount != 0) continue

                val intermediateType = staticGetter.returnType
                if (intermediateType.isPrimitive || intermediateType == String::class.java || intermediateType == Void.TYPE) {
                    continue
                }

                val managerGetter = runCatching {
                    intermediateType.declaredMethods.firstOrNull {
                        it.parameterCount == 0 && managerClass.isAssignableFrom(it.returnType)
                    }
                }.getOrNull() ?: continue

                val manager = runCatching {
                    staticGetter.isAccessible = true
                    managerGetter.isAccessible = true
                    val intermediate = staticGetter.invoke(null) ?: return@runCatching null
                    managerGetter.invoke(intermediate)
                }.getOrNull()

                if (manager != null) {
                    return ManagerResolution(
                        manager = manager,
                        utilityClass = utilityClass,
                        staticGetter = staticGetter,
                        intermediateClass = intermediateType,
                        managerGetter = managerGetter,
                    )
                }
            }
        }

        return null
    }

    private data class ManagerResolution(
        val manager: Any,
        val utilityClass: Class<*>,
        val staticGetter: Method,
        val intermediateClass: Class<*>,
        val managerGetter: Method,
    )

    private companion object {
        const val LUNAR_CLIENT_PACKAGE = "com.moonsworth.lunar.client."
        const val LUNAR_UTIL_PACKAGE = "com.moonsworth.lunar.client.util."
        val NOTIFICATION_PARAMETERS = arrayOf(String::class.java, String::class.java)
        val DISCOVERY_RETRY_NANOS: Long = TimeUnit.SECONDS.toNanos(5)
    }
}
