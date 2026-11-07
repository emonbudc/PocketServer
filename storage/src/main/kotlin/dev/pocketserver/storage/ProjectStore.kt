package dev.pocketserver.storage

import dev.pocketserver.core.util.PathGuard
import java.io.File

data class ProjectEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
)

class ProjectStore(private val paths: PocketServerPaths) {
    private val namePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun listProjects(): List<ProjectEntry> = listChildren(paths.htdocs, paths.htdocs, directoriesOnly = true)

    fun listEntries(project: String?, relativeDir: String = ""): List<ProjectEntry> {
        val base = if (project.isNullOrBlank()) paths.htdocs else projectDir(project)
        val dir = if (relativeDir.isBlank()) {
            base
        } else {
            PathGuard.resolveWithin(base, relativeDir).also {
                require(it.isDirectory) { "Not a directory: $relativeDir" }
            }
        }
        return listChildren(dir, base, directoriesOnly = false)
    }

    fun createProject(name: String, starterIndex: Boolean = true): File {
        require(namePattern.matches(name)) { "Project names may use letters, digits, dot, dash and underscore only" }
        val dir = PathGuard.resolveWithin(paths.htdocs, name)
        require(dir.mkdirs() || (dir.isDirectory && dir.list()?.isEmpty() == true)) { "Project already exists" }
        if (starterIndex) {
            val index = PathGuard.resolveWithin(dir, "index.php")
            if (!index.exists()) {
                index.writeText(
                    """
                    <!doctype html>
                    <html lang="en">
                    <head><meta charset="utf-8"><title>$name</title></head>
                    <body>
                    <h1>$name</h1>
                    <p>PocketServer project created ${java.time.LocalDate.now()}</p>
                    <?php echo '<p>PHP is ready.</p>'; ?>
                    </body>
                    </html>
                    """.trimIndent() + "\n",
                )
            }
        }
        return dir
    }

    fun createDirectory(project: String?, parentRelativeDir: String, name: String): File {
        require(namePattern.matches(name)) { "Invalid directory name" }
        val projectRoot = if (project.isNullOrBlank()) paths.htdocs else projectDir(project)
        val parent = if (parentRelativeDir.isBlank()) {
            projectRoot
        } else {
            PathGuard.resolveWithin(projectRoot, parentRelativeDir).also {
                require(it.isDirectory) { "Parent directory does not exist" }
            }
        }
        val dir = PathGuard.resolveWithin(parent, name)
        require(dir.mkdirs()) { "Directory already exists" }
        return dir
    }

    fun deleteProject(name: String) {
        val dir = projectDir(name)
        require(dir != paths.htdocs) { "Invalid project" }
        deleteRecursivelyGuarded(dir)
    }

    fun deleteEntry(project: String?, relativePath: String) {
        val parent = if (project.isNullOrBlank()) paths.htdocs else projectDir(project)
        val target = PathGuard.resolveWithin(parent, relativePath)
        require(target != paths.htdocs && PathGuard.isInside(paths.htdocs, target)) { "Invalid target" }
        deleteRecursivelyGuarded(target)
    }

    fun readText(project: String?, relativePath: String, maxBytes: Long = 512 * 1024): String {
        val target = entryFile(project, relativePath)
        require(target.isFile) { "Not a file: $relativePath" }
        require(target.length() <= maxBytes) { "File is too large to preview (${target.length()} bytes)" }
        return target.readText()
    }

    fun writeText(project: String?, relativePath: String, content: String) {
        writeBytes(project, relativePath, content.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(project: String?, relativePath: String, content: ByteArray) {
        val target = entryFile(project, relativePath)
        require(PathGuard.isValidRelativePath(relativePath)) { "Invalid path" }
        require(content.size.toLong() <= MAX_WRITE_BYTES) { "File is too large to write" }
        target.parentFile?.let { parent ->
            require(PathGuard.isInside(paths.htdocs, parent)) { "Invalid parent directory" }
            require(parent.mkdirs() || parent.isDirectory) { "Unable to create parent directory" }
        }
        target.writeBytes(content)
    }

    fun readBytes(project: String?, relativePath: String, maxBytes: Long = MAX_WRITE_BYTES): ByteArray {
        val target = entryFile(project, relativePath)
        require(target.isFile) { "Not a file: $relativePath" }
        require(target.length() <= maxBytes) { "File is too large (${target.length()} bytes)" }
        return target.readBytes()
    }

    private fun entryFile(project: String?, relativePath: String): File {
        if (!PathGuard.isValidRelativePath(relativePath)) {
            throw IllegalArgumentException("Invalid path: $relativePath")
        }
        val parent = if (project.isNullOrBlank()) paths.htdocs else projectDir(project)
        val target = PathGuard.resolveWithin(parent, relativePath)
        if (target != parent) {
            require(PathGuard.isInside(paths.htdocs, target)) { "Path escapes htdocs" }
        }
        return target
    }

    private fun projectDir(name: String): File {
        require(namePattern.matches(name)) { "Invalid project name" }
        return PathGuard.resolveWithin(paths.htdocs, name)
    }

    private fun listChildren(root: File, base: File, directoriesOnly: Boolean): List<ProjectEntry> {
        if (!root.isDirectory) return emptyList()
        val baseCanonical = base.canonicalFile
        return root.listFiles()
            .orEmpty()
            .filter { directoriesOnly.not() || it.isDirectory }
            .filter { PathGuard.isInside(paths.htdocs, it) }
            .map {
                ProjectEntry(
                    name = it.name,
                    path = it.relativeTo(baseCanonical).invariantSeparatorsPath,
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isFile) it.length() else 0L,
                    modifiedAtEpochMs = it.lastModified(),
                )
            }
            .sortedWith(compareByDescending<ProjectEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun deleteRecursivelyGuarded(target: File) {
        require(PathGuard.isInside(paths.htdocs, target)) { "Refusing to delete outside htdocs" }
        if (target.isDirectory) {
            target.listFiles().orEmpty().forEach { deleteRecursivelyGuarded(it) }
        }
        target.delete()
    }

    companion object {
        const val MAX_WRITE_BYTES = 64L * 1024L * 1024L
    }
}
