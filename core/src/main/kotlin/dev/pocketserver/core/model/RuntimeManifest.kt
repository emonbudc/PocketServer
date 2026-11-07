package dev.pocketserver.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuntimeManifest(
    @SerialName("formatVersion") val formatVersion: Int = 1,
    val id: String,
    val version: String,
    val abi: String = ABI_ARM64,
    val description: String? = null,
    val license: String? = null,
    val entrypoints: Map<String, String> = emptyMap(),
) {
    fun validationErrors(): List<String> = buildList {
        if (!ID_PATTERN.matches(id)) add("Invalid runtime id: '$id'")
        if (!VERSION_PATTERN.matches(version)) add("Invalid runtime version: '$version'")
        if (abi != ABI_ARM64) add("Unsupported ABI '$abi'; only $ABI_ARM64 is supported")
        if (formatVersion != 1) add("Unsupported manifest formatVersion $formatVersion")
        entrypoints.forEach { (name, path) ->
            if (!NAME_PATTERN.matches(name)) add("Invalid entrypoint name: '$name'")
            if (!dev.pocketserver.core.util.PathGuard.isValidRelativePath(path)) {
                add("Unsafe entrypoint path for '$name': '$path'")
            }
        }
    }

    companion object {
        const val ABI_ARM64 = "arm64-v8a"
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val VERSION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,63}")
        private val NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}

data class InstalledRuntime(
    val manifest: RuntimeManifest,
    val installPath: String,
    val installedAtEpochMs: Long,
    val verified: Boolean,
)
