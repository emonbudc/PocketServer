package dev.pocketserver.app

import android.content.Context
import android.os.Build
import dev.pocketserver.storage.AppLogger
import dev.pocketserver.storage.PocketServerPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ProbeResult(
    val success: Boolean,
    val output: String,
)

class NativeProbe(
    private val context: Context,
    private val paths: PocketServerPaths,
    private val logger: AppLogger,
) {
    suspend fun run(): ProbeResult = withContext(Dispatchers.IO) {
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) {
            return@withContext ProbeResult(
                false,
                "This device does not support arm64-v8a. PocketServer native execution requires ARM64.",
            )
        }
        val executable = try {
            installProbe()
        } catch (exception: Exception) {
            logger.error("probe", "Unable to install native probe", exception)
            return@withContext ProbeResult(
                false,
                "Unable to prepare the native probe: ${exception.message}",
            )
        }
        val process = try {
            ProcessBuilder(executable.absolutePath)
                .directory(paths.temp)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment()["PATH"] = "/system/bin:/system/xbin"
                    environment()["HOME"] = paths.temp.absolutePath
                    environment()["TMPDIR"] = paths.temp.absolutePath
                }
                .start()
        } catch (exception: Exception) {
            logger.error("probe", "Unable to execute native probe", exception)
            return@withContext ProbeResult(
                false,
                "Unable to execute the native probe: ${exception.message}",
            )
        }
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        val output = runCatching {
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val exit = runCatching { process.exitValue() }.getOrNull()
        val success = finished && exit == 0
        val text = buildString {
            append(output.trimEnd())
            if (output.isNotBlank()) appendLine()
            appendLine()
            appendLine("binary: ${executable.absolutePath}")
            append("exit:   ${exit ?: "killed after timeout"}")
        }
        logger.info("probe", "Native probe success=$success exit=$exit")
        ProbeResult(success, text)
    }

    private fun installProbe(): File {
        paths.ensureLayout()
        val destination = File(paths.bin, "psprobe")
        val temporary = File.createTempFile("psprobe-", ".tmp", paths.temp)
        try {
            context.assets.open(ASSET_PATH).use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            if (!temporary.setExecutable(true, true)) {
                throw IllegalStateException("chmod failed for the native probe")
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Unable to replace ${destination.absolutePath}")
            }
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Unable to move the native probe into ${paths.bin}")
            }
            if (!destination.setExecutable(true, true)) {
                throw IllegalStateException("chmod failed for ${destination.absolutePath}")
            }
            return destination
        } finally {
            temporary.delete()
        }
    }

    companion object {
        private const val ASSET_PATH = "arm64-v8a/psprobe"
    }
}
