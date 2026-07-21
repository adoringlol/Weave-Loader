package net.weavemc.loader.impl.lunar

import me.xtrm.klog.dsl.klog
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
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

    private data class BuildIdentity(val id: String, val summary: String)

    private data class CachedAccess(
        val notification: Method,
        val staticGetter: Method,
        val managerGetter: Method,
    )

    fun load(loader: ClassLoader): Resolution? {
        val build = detectBuild(loader) ?: return null
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

            if (!fingerprintMatches(managerClass, properties.getProperty("manager.fingerprint")) ||
                !fingerprintMatches(utilityClass, properties.getProperty("utility.fingerprint")) ||
                !fingerprintMatches(intermediateClass, properties.getProperty("intermediate.fingerprint"))
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
        val build = detectBuild(loader) ?: return
        val file = cacheFile(build.id)

        runCatching {
            Files.createDirectories(file.parent)
            val properties = Properties().apply {
                setProperty("format", FORMAT_VERSION)
                setProperty("build", build.summary)
                setProperty("manager.class", discovery.manager.javaClass.name)
                setProperty("manager.fingerprint", fingerprint(discovery.manager.javaClass))
                setProperty("notification.method", discovery.notificationMethod.name)
                setProperty("utility.class", discovery.utilityClass.name)
                setProperty("utility.fingerprint", fingerprint(discovery.utilityClass))
                setProperty("utility.getter", discovery.staticGetter.name)
                setProperty("intermediate.class", discovery.intermediateClass.name)
                setProperty("intermediate.fingerprint", fingerprint(discovery.intermediateClass))
                setProperty("manager.getter", discovery.managerGetter.name)
            }

            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.newOutputStream(temporary).use { properties.store(it, "Weave Lunar notification cache") }
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
            logger.info("Saved Lunar notification resolution cache for ${build.summary}")
        }.onFailure {
            logger.debug("Could not save Lunar notification resolution cache", it)
        }
    }

    private fun detectBuild(loader: ClassLoader): BuildIdentity? = runCatching {
        val bytes = loader.getResourceAsStream(BUILD_DATA_RESOURCE)?.use(::readAll) ?: return null
        val properties = Properties().apply { bytes.inputStream().use(::load) }
        val hash = sha256(bytes)
        val version = properties.getProperty("lunarVersion")?.takeIf(String::isNotBlank) ?: "unknown"
        val git = properties.getProperty("gitHash")?.takeIf(String::isNotBlank)
            ?: properties.getProperty("fullGitHash")?.takeIf(String::isNotBlank)
            ?: "unknown"
        val proguard = properties.getProperty("proguardUuid")?.takeIf(String::isNotBlank) ?: "unknown"
        BuildIdentity(hash, "$version/$git/$proguard")
    }.getOrNull()

    private fun cacheFile(buildId: String): Path {
        val override = System.getProperty("weave.lunar.cache.dir")
            ?: System.getProperty("agentloader.cache.dir")
        val root = if (override.isNullOrBlank()) {
            Paths.get(System.getProperty("user.home"), ".weave", "cache")
        } else {
            Paths.get(override)
        }
        return root.resolve("lunar-notifications").resolve("$buildId.properties")
    }

    private fun loadLunarClass(loader: ClassLoader, name: String?): Class<*> {
        val value = name ?: error("missing cached class name")
        require(value.startsWith(LUNAR_PACKAGE)) { "cached class is outside Lunar" }
        return Class.forName(value, false, loader)
    }

    private fun fingerprintMatches(type: Class<*>, expected: String?): Boolean =
        !expected.isNullOrBlank() && fingerprint(type) == expected

    private fun fingerprint(type: Class<*>): String {
        val resource = "/${type.name.replace('.', '/')}.class"
        val bytecode = type.getResourceAsStream(resource)?.use(::readAll)
        if (bytecode != null) return "bytecode:${sha256(bytecode)}"

        val shape = buildString {
            append(type.name).append('|').append(type.modifiers).append('|')
            type.declaredFields
                .map { "${it.name}:${it.type.name}:${it.modifiers}" }
                .sorted()
                .forEach { append(it).append(';') }
            append('|')
            type.declaredConstructors
                .map { constructor -> constructor.parameterTypes.joinToString(",", prefix = "(", postfix = ")") { it.name } }
                .sorted()
                .forEach { append(it).append(';') }
            append('|')
            type.declaredMethods
                .map { method ->
                    method.parameterTypes.joinToString(",", prefix = "${method.name}(", postfix = ")") { it.name } +
                            ":${method.returnType.name}:${method.modifiers}"
                }
                .sorted()
                .forEach { append(it).append(';') }
        }
        return "shape:${sha256(shape.toByteArray(StandardCharsets.UTF_8))}"
    }

    private fun invalidate(file: Path, reason: String) {
        logger.info("Invalidating Lunar notification cache: $reason")
        runCatching { Files.deleteIfExists(file) }
    }

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key)?.takeIf(String::isNotBlank) ?: error("missing $key")

    private fun readAll(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val FORMAT_VERSION = "1"
    private const val BUILD_DATA_RESOURCE = "lunarBuildData.txt"
    private const val LUNAR_PACKAGE = "com.moonsworth.lunar."
}
