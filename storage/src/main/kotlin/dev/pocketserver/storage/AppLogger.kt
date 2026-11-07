package dev.pocketserver.storage

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Instant

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class AppLogger(private val paths: PocketServerPaths) {
    private val lock = Any()
    private val maxBytes = 5L * 1024L * 1024L

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        paths.ensureLayout()
        val file = paths.appLogFile()
        synchronized(lock) {
            rotateIfNeeded(file)
            file.parentFile?.mkdirs()
            PrintWriter(FileWriter(file, true)).use { writer ->
                writer.print("${Instant.now()} [${level.name}] [$tag] $message")
                if (throwable != null) {
                    writer.print(": ${throwable.javaClass.simpleName}: ${throwable.message}")
                    if (level == LogLevel.ERROR) {
                        writer.println()
                        throwable.printStackTrace(writer)
                    }
                }
                writer.println()
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.isFile || file.length() < maxBytes) return
        val rotated = File(file.parentFile, "${file.name}.1")
        rotated.delete()
        file.renameTo(rotated)
    }
}
