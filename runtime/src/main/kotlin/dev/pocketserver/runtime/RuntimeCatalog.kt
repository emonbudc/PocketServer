package dev.pocketserver.runtime

import dev.pocketserver.core.util.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class RuntimeBundleDescriptor(
    val id: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long = 0,
    val description: String? = null,
) {
    fun validationErrors(): List<String> = buildList {
        if (!ID_PATTERN.matches(id)) add("Invalid bundle id: '$id'")
        if (!VERSION_PATTERN.matches(version)) add("Invalid bundle version: '$version'")
        if (!Sha256.isHexDigest(sha256)) add("Bundle '$id' has an invalid SHA-256 digest")
        if (sizeBytes < 0 || sizeBytes > MAX_ARCHIVE_BYTES) {
            add("Bundle '$id' size must be between 0 and $MAX_ARCHIVE_BYTES bytes")
        }
        val parsed = runCatching { URI(url) }.getOrNull()
        if (parsed == null || parsed.scheme != "https" || parsed.host.isNullOrBlank()) {
            add("Bundle '$id' URL must be an absolute HTTPS URL")
        } else if (parsed.userInfo != null) {
            add("Bundle '$id' URL must not contain credentials")
        }
    }

    companion object {
        const val MAX_ARCHIVE_BYTES: Long = 2L * 1024 * 1024 * 1024
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val VERSION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,63}")
    }
}

@Serializable
data class RuntimeCatalog(
    val formatVersion: Int = 1,
    val bundles: List<RuntimeBundleDescriptor> = emptyList(),
) {
    fun validationErrors(): List<String> = buildList {
        if (formatVersion != 1) add("Unsupported catalog formatVersion $formatVersion")
        bundles.forEach { addAll(it.validationErrors()) }
        val duplicates = bundles.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            add("Duplicate bundle ids: ${duplicates.joinToString(", ")}")
        }
    }
}

class RuntimeCatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)

object RuntimeCatalogCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    fun decode(text: String): RuntimeCatalog {
        val catalog = try {
            json.decodeFromString(RuntimeCatalog.serializer(), text)
        } catch (exception: SerializationException) {
            throw RuntimeCatalogException("Invalid runtime catalog: ${exception.message}", exception)
        } catch (exception: IllegalArgumentException) {
            throw RuntimeCatalogException("Invalid runtime catalog: ${exception.message}", exception)
        }
        val errors = catalog.validationErrors()
        if (errors.isNotEmpty()) {
            throw RuntimeCatalogException("Invalid runtime catalog: ${errors.joinToString("; ")}")
        }
        return catalog
    }
}
