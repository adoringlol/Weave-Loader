package net.weavemc.loader.impl.lunar

import me.xtrm.klog.dsl.klog
import net.weavemc.api.lunar.LunarRuntimeProvider
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Gives mods the two things they need to integrate with Lunar's obfuscated code: a view of the
 * Lunar classes that are already loaded, and a persistent cache that survives until Lunar updates.
 *
 * Cache files live in `~/.weave/cache/lunar/<namespace>/<build id>.properties`. Writing a namespace
 * deletes the files of older builds, so each namespace holds at most one resolution.
 */
internal class LunarRuntimeService(
    private val instrumentation: Instrumentation,
) : LunarRuntimeProvider {
    private val logger by klog

    private val persistentCacheEnabled: Boolean =
        System.getProperty("weave.lunar.cache")?.toBooleanStrictOrNull() ?: true

    override fun loadedClasses(prefix: String): List<Class<*>> {
        val loaded = instrumentation.allLoadedClasses
        val matches = ArrayList<Class<*>>()
        for (type in loaded) {
            if (type.name.startsWith(prefix)) matches.add(type)
        }
        return matches
    }

    override fun buildId(loader: ClassLoader): String? = LunarBuild.detect(loader)?.id

    override fun readCache(namespace: String, loader: ClassLoader): Map<String, String>? {
        if (!persistentCacheEnabled) return null
        val build = LunarBuild.detect(loader) ?: return null
        val file = cacheFile(namespace, build.id)
        if (!Files.isRegularFile(file)) return null

        return runCatching {
            val properties = Properties()
            Files.newInputStream(file).use(properties::load)
            if (properties.getProperty(FORMAT_KEY) != FORMAT_VERSION) {
                invalidate(namespace, file, "unsupported format")
                return null
            }

            val entries = HashMap<String, String>()
            for (key in properties.stringPropertyNames()) {
                when {
                    key.startsWith(FINGERPRINT_PREFIX) -> {
                        val type = Class.forName(key.substring(FINGERPRINT_PREFIX.length), false, loader)
                        if (!LunarBuild.fingerprintMatches(type, properties.getProperty(key))) {
                            invalidate(namespace, file, "fingerprint of ${type.name} changed")
                            return null
                        }
                    }
                    key.startsWith(ENTRY_PREFIX) -> entries[key.substring(ENTRY_PREFIX.length)] = properties.getProperty(key)
                }
            }
            logger.info("Loaded Lunar cache '$namespace' for ${build.summary}")
            entries
        }.getOrElse {
            invalidate(namespace, file, it.message ?: it.javaClass.simpleName)
            null
        }
    }

    override fun writeCache(
        namespace: String,
        loader: ClassLoader,
        entries: Map<String, String>,
        fingerprinted: Collection<Class<*>>,
    ): Boolean {
        if (!persistentCacheEnabled) return false
        val build = LunarBuild.detect(loader) ?: return false
        val file = cacheFile(namespace, build.id)

        return runCatching {
            val properties = Properties()
            properties.setProperty(FORMAT_KEY, FORMAT_VERSION)
            properties.setProperty(BUILD_KEY, build.summary)
            for ((key, value) in entries) properties.setProperty(ENTRY_PREFIX + key, value)
            for (type in fingerprinted) properties.setProperty(FINGERPRINT_PREFIX + type.name, LunarBuild.fingerprint(type))
            LunarBuild.writeAtomically(file, properties, "Weave Lunar cache: $namespace")
            deleteOtherBuilds(file)
            logger.info("Saved Lunar cache '$namespace' for ${build.summary}")
            true
        }.getOrElse {
            logger.warn("Could not save Lunar cache '$namespace'", it)
            false
        }
    }

    private fun cacheFile(namespace: String, buildId: String): Path {
        require(NAMESPACE.matches(namespace)) { "Invalid cache namespace: $namespace" }
        return LunarBuild.cacheRoot().resolve("lunar").resolve(namespace).resolve("$buildId.properties")
    }

    private fun deleteOtherBuilds(current: Path) {
        Files.newDirectoryStream(current.parent, "*.properties").use { files ->
            for (file in files) {
                if (file.fileName != current.fileName) runCatching { Files.deleteIfExists(file) }
            }
        }
    }

    private fun invalidate(namespace: String, file: Path, reason: String) {
        logger.info("Invalidating Lunar cache '$namespace': $reason")
        runCatching { Files.deleteIfExists(file) }
    }

    private companion object {
        const val FORMAT_KEY = "format"
        const val FORMAT_VERSION = "1"
        const val BUILD_KEY = "build"
        const val ENTRY_PREFIX = "entry."
        const val FINGERPRINT_PREFIX = "fingerprint."
        val NAMESPACE = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}
