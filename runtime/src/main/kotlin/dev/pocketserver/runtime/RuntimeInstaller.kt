package dev.pocketserver.runtime

import dev.pocketserver.core.model.InstalledRuntime
import dev.pocketserver.core.model.RuntimeManifest
import dev.pocketserver.core.util.PathGuard
import dev.pocketserver.core.util.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class RuntimeInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class InstalledRuntimeMetadata(
    val manifest: RuntimeManifest,
    val installedAtEpochMs: Long,
    val verified: Boolean,
    val archiveSha256: String,
    val totalBytes: Long,
    val fileCount: Int,
)

data class RuntimeInstallResult(
    val installed: InstalledRuntime,
    val metadata: InstalledRuntimeMetadata,
    val computedSha256: String,
)

class RuntimeInstaller(
    private val directories: RuntimeDirectories,
    private val maxArchiveEntries: Int = 200_000,
    private val maxUncompressedBytes: Long = 4L * 1024 * 1024 * 1024,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    fun install(
        archive: File,
        expectedSha256: String? = null,
        overwrite: Boolean = false,
        requireVerification: Boolean = false,
    ): RuntimeInstallResult {
        if (!archive.isFile) throw RuntimeInstallException("Runtime archive not found: ${archive.name}")
        if (expectedSha256 != null && !Sha256.isHexDigest(expectedSha256)) {
            throw RuntimeInstallException("Expected SHA-256 must be 64 hexadecimal characters")
        }
        if (expectedSha256 == null && requireVerification) {
            throw RuntimeInstallException("A SHA-256 checksum is required for this import")
        }

        val computed = Sha256.hashFile(archive)
        val verified = expectedSha256 != null && Sha256.matches(expectedSha256, computed)
        if (expectedSha256 != null && !verified) {
            throw RuntimeInstallException("SHA-256 mismatch. Expected $expectedSha256, got $computed")
        }

        directories.root.mkdirs()
        directories.packages.mkdirs()
        directories.temp.mkdirs()

        ZipFile(archive).use { zip ->
            val manifest = readManifest(zip)
            val errors = manifest.validationErrors()
            if (errors.isNotEmpty()) {
                throw RuntimeInstallException("Invalid runtime manifest: ${errors.joinToString("; ")}")
            }
            validateEntries(zip)

            val staging = File(directories.temp, "install-${UUID.randomUUID()}")
            if (!staging.mkdirs()) throw RuntimeInstallException("Unable to create staging directory")
            try {
                val extracted = extract(zip, staging)
                validateEntrypoints(staging, manifest)
                val target = PathGuard.resolveWithin(
                    directories.packages,
                    "${manifest.id}/${manifest.version}",
                )
                if (target.exists() && !overwrite) {
                    throw RuntimeInstallException(
                        "Runtime ${manifest.id} ${manifest.version} is already installed",
                    )
                }
                target.parentFile?.mkdirs()
                if (target.exists()) {
                    require(target.deleteRecursively()) { "Unable to replace ${target.absolutePath}" }
                }
                if (!staging.renameTo(target)) {
                    throw RuntimeInstallException("Unable to move runtime into place")
                }

                val metadata = InstalledRuntimeMetadata(
                    manifest = manifest,
                    installedAtEpochMs = System.currentTimeMillis(),
                    verified = verified,
                    archiveSha256 = computed,
                    totalBytes = extracted.totalBytes,
                    fileCount = extracted.fileCount,
                )
                val metadataFile = PathGuard.resolveWithin(target, METADATA_FILE)
                metadataFile.writeText(json.encodeToString(InstalledRuntimeMetadata.serializer(), metadata))
                val manifestFile = PathGuard.resolveWithin(target, MANIFEST_FILE)
                manifestFile.writeText(json.encodeToString(RuntimeManifest.serializer(), manifest))
                PathGuard.resolveWithin(directories.packages, "${manifest.id}/active")
                    .writeText(manifest.version)

                makeEntrypointsExecutable(target, manifest)

                return RuntimeInstallResult(
                    installed = InstalledRuntime(
                        manifest = manifest,
                        installPath = target.absolutePath,
                        installedAtEpochMs = metadata.installedAtEpochMs,
                        verified = verified,
                    ),
                    metadata = metadata,
                    computedSha256 = computed,
                )
            } catch (exception: Exception) {
                staging.deleteRecursively()
                throw exception
            }
        }
    }

    fun listInstalled(): List<InstalledRuntime> {
        if (!directories.packages.isDirectory) return emptyList()
        return directories.packages.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { packageDir -> readInstalled(packageDir.name) }
            .sortedBy { it.manifest.id }
    }

    fun findInstalled(id: String): InstalledRuntime? = readInstalled(id)

    fun activeEntrypoint(id: String, entrypoint: String): File? {
        val installed = readInstalled(id) ?: return null
        val relative = installed.manifest.entrypoints[entrypoint] ?: return null
        val file = PathGuard.resolveWithin(File(installed.installPath), relative)
        return file.takeIf { it.isFile }
    }

    fun uninstall(id: String): Boolean {
        val dir = PathGuard.resolveWithin(directories.packages, id)
        if (dir == directories.packages || !dir.exists()) return false
        if (!PathGuard.isInside(directories.packages, dir)) return false
        return dir.deleteRecursively()
    }

    private fun readInstalled(id: String): InstalledRuntime? {
        return try {
            val packageDir = PathGuard.resolveWithin(directories.packages, id)
            if (!packageDir.isDirectory) return null
            val activeFile = PathGuard.resolveWithin(packageDir, "active")
            if (!activeFile.isFile) return null
            val version = activeFile.readText().trim()
            if (version.isBlank()) return null
            val versionDir = PathGuard.resolveWithin(packageDir, version)
            val metadataFile = PathGuard.resolveWithin(versionDir, METADATA_FILE)
            if (!metadataFile.isFile) return null
            val metadata = json.decodeFromString(
                InstalledRuntimeMetadata.serializer(),
                metadataFile.readText(),
            )
            InstalledRuntime(
                manifest = metadata.manifest,
                installPath = versionDir.absolutePath,
                installedAtEpochMs = metadata.installedAtEpochMs,
                verified = metadata.verified,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readManifest(zip: ZipFile): RuntimeManifest {
        val entry = zip.getEntry(MANIFEST_FILE)
            ?: throw RuntimeInstallException("Runtime archive is missing $MANIFEST_FILE")
        if (entry.isDirectory) throw RuntimeInstallException("$MANIFEST_FILE must be a file")
        if (entry.size > MAX_MANIFEST_BYTES) throw RuntimeInstallException("Manifest is too large")
        val bytes = zip.getInputStream(entry).use { input ->
            val data = input.readNBytes(MAX_MANIFEST_BYTES.toInt() + 1)
            if (data.size > MAX_MANIFEST_BYTES) throw RuntimeInstallException("Manifest is too large")
            data
        }
        return try {
            json.decodeFromString(RuntimeManifest.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (exception: Exception) {
            throw RuntimeInstallException("Unable to parse runtime manifest", exception)
        }
    }

    private fun validateEntries(zip: ZipFile) {
        val entries = java.util.Collections.list(zip.entries())
        if (entries.size > maxArchiveEntries) {
            throw RuntimeInstallException("Archive contains too many files (${entries.size})")
        }
        var declaredBytes = 0L
        entries.forEach { entry ->
            val name = entry.name
            val normalized = name.removeSuffix("/")
            if (normalized.isEmpty() || !PathGuard.isValidRelativePath(normalized)) {
                throw RuntimeInstallException("Unsafe archive entry: $name")
            }
            if (entry.size > 0) {
                declaredBytes += entry.size
                if (declaredBytes > maxUncompressedBytes) {
                    throw RuntimeInstallException("Archive expands beyond the size limit")
                }
            }
        }
    }

    private data class ExtractSummary(val fileCount: Int, val totalBytes: Long)

    private fun extract(zip: ZipFile, staging: File): ExtractSummary {
        var fileCount = 0
        var totalBytes = 0L
        for (entry in zip.entries()) {
            val target = PathGuard.resolveWithin(staging, entry.name.removeSuffix("/"))
            if (entry.isDirectory) {
                target.mkdirs()
                continue
            }
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > maxUncompressedBytes) {
                            throw RuntimeInstallException("Archive expands beyond the size limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            fileCount++
        }
        return ExtractSummary(fileCount, totalBytes)
    }

    private fun validateEntrypoints(staging: File, manifest: RuntimeManifest) {
        if (manifest.entrypoints.isEmpty()) {
            throw RuntimeInstallException("Runtime manifest does not declare any entrypoints")
        }
        manifest.entrypoints.forEach { (name, relative) ->
            val file = PathGuard.resolveWithin(staging, relative)
            if (!file.isFile) throw RuntimeInstallException("Entrypoint '$name' is missing: $relative")
            if (!ElfInspector.isUsableAarch64Executable(file)) {
                val info = ElfInspector.inspect(file)
                throw RuntimeInstallException(
                    "Entrypoint '$name' is not an AArch64 executable (${info?.describe() ?: "not an ELF file"})",
                )
            }
        }
    }

    private fun makeEntrypointsExecutable(root: File, manifest: RuntimeManifest) {
        manifest.entrypoints.values.forEach { relative ->
            PathGuard.resolveWithin(root, relative).setExecutable(true, false)
        }
        manifest.entrypoints.values
            .map { File(it).parent }
            .filterNotNull()
            .distinct()
            .forEach { relativeDir ->
                PathGuard.resolveWithin(root, relativeDir).setExecutable(true, false)
            }
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val METADATA_FILE = "installed.json"
        private const val MAX_MANIFEST_BYTES = 256L * 1024L
    }
}
