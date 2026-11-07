package dev.pocketserver.storage

import android.content.Context
import dev.pocketserver.core.config.RuntimeLayout
import dev.pocketserver.core.util.PathGuard
import java.io.File

class PocketServerPaths(context: Context) {
    val root: File = File(context.filesDir, RuntimeLayout.ROOT).canonicalFile
    val runtime: File get() = child(RuntimeLayout.RUNTIME)
    val packages: File get() = child(RuntimeLayout.PACKAGES)
    val bin: File get() = child(RuntimeLayout.BIN)
    val config: File get() = child(RuntimeLayout.CONFIG)
    val htdocs: File get() = child(RuntimeLayout.HTDOCS)
    val data: File get() = child(RuntimeLayout.DATA)
    val logs: File get() = child(RuntimeLayout.LOGS)
    val backups: File get() = child(RuntimeLayout.BACKUPS)
    val temp: File get() = child(RuntimeLayout.TEMP)

    fun ensureLayout(): File {
        require(root.mkdirs() || root.isDirectory) { "Unable to create ${root.absolutePath}" }
        RuntimeLayout.directories.forEach { relative ->
            val dir = PathGuard.resolveWithin(root, relative)
            require(dir.mkdirs() || dir.isDirectory) { "Unable to create ${dir.absolutePath}" }
        }
        return root
    }

    fun child(relativePath: String): File = PathGuard.resolveWithin(root, relativePath)

    fun packageDir(id: String): File = PathGuard.resolveWithin(packages, id)

    fun packageVersionDir(id: String, version: String): File =
        PathGuard.resolveWithin(packageDir(id), version)

    fun entrypoint(id: String, version: String, relativePath: String): File =
        PathGuard.resolveWithin(packageVersionDir(id, version), relativePath)

    fun serviceLogDir(serviceId: String): File =
        PathGuard.resolveWithin(logs, RuntimeLayout.serviceLogDirectories[serviceId] ?: "app")

    fun appLogFile(): File = PathGuard.resolveWithin(logs, "app/pocketserver.log")
}
