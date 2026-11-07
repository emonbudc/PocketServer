package dev.pocketserver.storage

import dev.pocketserver.core.util.PathGuard
import java.io.File
import java.io.RandomAccessFile

data class LogFileInfo(
    val file: File,
    val label: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
)

class LogStore(private val paths: PocketServerPaths) {
    fun listLogs(): List<LogFileInfo> {
        val root = paths.logs
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && it.length() > 0 }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                LogFileInfo(file, relative, file.length(), file.lastModified())
            }
            .sortedByDescending { it.modifiedAtEpochMs }
            .toList()
    }

    fun readTail(file: File, maxLines: Int = 500, maxBytes: Int = 256 * 1024): String {
        require(PathGuard.isInside(paths.logs, file)) { "Log file is outside the log directory" }
        if (!file.isFile) return ""
        val length = file.length()
        val bytesToRead = minOf(length, maxBytes.toLong()).toInt()
        RandomAccessFile(file, "r").use { random ->
            random.seek(length - bytesToRead)
            val buffer = ByteArray(bytesToRead)
            random.readFully(buffer)
            val text = String(buffer, Charsets.UTF_8)
            if (bytesToRead.toLong() < length) {
                val firstBreak = text.indexOf('\n')
                if (firstBreak >= 0) return text.substring(firstBreak + 1).lines().takeLast(maxLines).joinToString("\n")
            }
            return text.lines().takeLast(maxLines).joinToString("\n")
        }
    }

    fun delete(file: File) {
        require(PathGuard.isInside(paths.logs, file)) { "Log file is outside the log directory" }
        file.delete()
    }
}
