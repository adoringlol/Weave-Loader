package net.weavemc.loader.impl.lunar

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

/**
 * Identity and fingerprinting helpers shared by every build-scoped Lunar cache.
 *
 * A cache is keyed by the hash of Lunar's `lunarBuildData.txt`, so it is renewed whenever Lunar ships a new
 * build. Class fingerprints additionally catch bytecode changes that do not change the displayed build data.
 */
internal object LunarBuild {
    data class Identity(val id: String, val summary: String)

    fun detect(loader: ClassLoader): Identity? = runCatching {
        val bytes = loader.getResourceAsStream(BUILD_DATA_RESOURCE)?.use(::readAll) ?: return null
        val properties = Properties().apply { bytes.inputStream().use(::load) }
        val version = properties.getProperty("lunarVersion")?.takeIf(String::isNotBlank) ?: "unknown"
        val git = properties.getProperty("gitHash")?.takeIf(String::isNotBlank)
            ?: properties.getProperty("fullGitHash")?.takeIf(String::isNotBlank)
            ?: "unknown"
        val proguard = properties.getProperty("proguardUuid")?.takeIf(String::isNotBlank) ?: "unknown"
        Identity(sha256(bytes), "$version/$git/$proguard")
    }.getOrNull()

    fun cacheRoot(): Path {
        val override = System.getProperty("weave.lunar.cache.dir")
            ?: System.getProperty("agentloader.cache.dir")
        return if (override.isNullOrBlank()) {
            Paths.get(System.getProperty("user.home"), ".weave", "cache")
        } else {
            Paths.get(override)
        }
    }

    fun fingerprint(type: Class<*>): String {
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

    fun fingerprintMatches(type: Class<*>, expected: String?): Boolean =
        !expected.isNullOrBlank() && fingerprint(type) == expected

    fun writeAtomically(file: Path, properties: Properties, comment: String) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { properties.store(it, comment) }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun readAll(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val BUILD_DATA_RESOURCE = "lunarBuildData.txt"
}
