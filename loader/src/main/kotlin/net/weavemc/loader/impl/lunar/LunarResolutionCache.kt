package net.weavemc.loader.impl.lunar

import me.xtrm.klog.dsl.klog
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Persistent, build-scoped cache for Lunar's obfuscated notification access chain. */
internal object LunarResolutionCache {
    private val logger by klog

    data class Resolution(val manager: Any, val notificationMethod: Method)

    data class Discovery(
        val manager: Any,
        val notificationMethod: Method,
        val utilityClass: Class<*>,
        val staticGetter: Method,
        val intermediateClass: Class<*>,
        val managerGetter: Method,
    )

    private data class CachedAccess(
        val notification: Method,
        val staticGetter: Method,
        val managerGetter: Method,
    )

    fun load(loader: ClassLoader): Resolution? {
        val build = LunarBuild.detect(loader) ?: return null
        val file = cacheFile(build.id)
        if (!Files.isRegularFile(file)) return null

        val access = runCatching {
            val properties = Properties()
            Files.newInputStream(file).use(properties::load)
            if (properties.getProperty("format") != FORMAT_VERSION) {
                invalidate(file, "unsupported format")
                return null
            }

            val managerClass = loadLunarClass(loader, properties.getProperty("manager.class"))
            val utilityClass = loadLunarClass(loader, properties.getProperty("utility.class"))
            val intermediateClass = loadLunarClass(loader, properties.getProperty("intermediate.class"))

            if (!LunarBuild.fingerprintMatches(managerClass, properties.getProperty("manager.fingerprint")) ||
                !LunarBuild.fingerprintMatches(utilityClass, properties.getProperty("utility.fingerprint")) ||
                !LunarBuild.fingerprintMatches(intermediateClass, properties.getProperty("intermediate.fingerprint"))
            ) {
                invalidate(file, "class fingerprint changed")
                return null
            }

            val notification = managerClass.getDeclaredMethod(
                required(properties, "notification.method"),
                String::class.java,
                String::class.java,
            )
            val staticGetter = utilityClass.getDeclaredMethod(required(properties, "utility.getter"))
            val managerGetter = intermediateClass.getDeclaredMethod(required(properties, "manager.getter"))

            require(Modifier.isStatic(staticGetter.modifiers)) { "cached utility getter is not static" }
            require(intermediateClass.isAssignableFrom(staticGetter.returnType)) { "cached intermediate type changed" }
            require(managerClass.isAssignableFrom(managerGetter.returnType)) { "cached manager getter type changed" }

            notification.isAccessible = true
            staticGetter.isAccessible = true
            managerGetter.isAccessible = true

            CachedAccess(notification, staticGetter, managerGetter)
        }.getOrElse {
            invalidate(file, it.message ?: it.javaClass.simpleName)
            return null
        }

        // The classes can be loaded before Lunar has created its singleton graph.
        // That is a transient startup state, not cache corruption, so keep the file.
        return runCatching {
            val intermediate = access.staticGetter.invoke(null) ?: return@runCatching null
            val manager = access.managerGetter.invoke(intermediate) ?: return@runCatching null
            logger.info("Loaded Lunar notification resolution cache for ${build.summary}")
            Resolution(manager, access.notification)
        }.getOrElse {
            logger.debug("Cached Lunar notification graph is not ready yet", it)
            null
        }
    }

    fun save(loader: ClassLoader, discovery: Discovery) {
        val build = LunarBuild.detect(loader) ?: return
        val file = cacheFile(build.id)

        runCatching {
            val properties = Properties().apply {
                setProperty("format", FORMAT_VERSION)
                setProperty("build", build.summary)
                setProperty("manager.class", discovery.manager.javaClass.name)
                setProperty("manager.fingerprint", LunarBuild.fingerprint(discovery.manager.javaClass))
                setProperty("notification.method", discovery.notificationMethod.name)
                setProperty("utility.class", discovery.utilityClass.name)
                setProperty("utility.fingerprint", LunarBuild.fingerprint(discovery.utilityClass))
                setProperty("utility.getter", discovery.staticGetter.name)
                setProperty("intermediate.class", discovery.intermediateClass.name)
                setProperty("intermediate.fingerprint", LunarBuild.fingerprint(discovery.intermediateClass))
                setProperty("manager.getter", discovery.managerGetter.name)
            }
            LunarBuild.writeAtomically(file, properties, "Weave Lunar notification cache")
            logger.info("Saved Lunar notification resolution cache for ${build.summary}")
        }.onFailure {
            logger.debug("Could not save Lunar notification resolution cache", it)
        }
    }

    private fun cacheFile(buildId: String): Path =
        LunarBuild.cacheRoot().resolve("lunar-notifications").resolve("$buildId.properties")

    private fun loadLunarClass(loader: ClassLoader, name: String?): Class<*> {
        val value = name ?: error("missing cached class name")
        require(value.startsWith(LUNAR_PACKAGE)) { "cached class is outside Lunar" }
        return Class.forName(value, false, loader)
    }

    private fun invalidate(file: Path, reason: String) {
        logger.info("Invalidating Lunar notification cache: $reason")
        runCatching { Files.deleteIfExists(file) }
    }

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key)?.takeIf(String::isNotBlank) ?: error("missing $key")

    private const val FORMAT_VERSION = "1"
    private const val LUNAR_PACKAGE = "com.moonsworth.lunar."
}
