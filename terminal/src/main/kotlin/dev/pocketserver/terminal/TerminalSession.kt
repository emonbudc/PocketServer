package dev.pocketserver.terminal

import dev.pocketserver.core.util.PathGuard
import dev.pocketserver.core.util.TokenParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

class TerminalSession(
    private val documentRoot: File,
    private val commandResolver: TerminalCommandResolver,
    private val historySize: Int = 100,
    private val allowedCommands: Set<String> = DEFAULT_ALLOWED_COMMANDS,
    private val environmentProvider: () -> Map<String, String> = { emptyMap() },
    private val timeoutMs: Long = 30_000,
    private val maxOutputBytes: Int = 256 * 1024,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = ArrayDeque<String>()

    fun history(): List<String> = history.toList()

    suspend fun execute(input: String): TerminalResult {
        val startedAt = System.currentTimeMillis()
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return TerminalResult("", "", 0, 0)
        }
        history.addLast(trimmed)
        while (history.size > historySize) history.removeFirst()

        val parsed = TokenParser.parse(trimmed)
        if (!parsed.ok) {
            return error(trimmed, parsed.error ?: "Unable to parse command", startedAt)
        }
        val tokens = parsed.tokens
        if (tokens.isEmpty()) return TerminalResult(trimmed, "", 0, 0)
        if (tokens.size > 64) return error(trimmed, "Too many arguments", startedAt)
        if (tokens.any { it.length > 8192 }) return error(trimmed, "Argument is too long", startedAt)

        return when (val command = tokens.first()) {
            "help" -> builtin(trimmed, helpText(), startedAt)
            "pwd" -> builtin(trimmed, documentRoot.absolutePath, startedAt)
            "clear" -> TerminalResult(trimmed, "", 0, elapsed(startedAt), clearScreen = true)
            "ls" -> list(tokens.drop(1), trimmed, startedAt)
            "cat" -> cat(tokens.drop(1), trimmed, startedAt)
            "cd" -> error(trimmed, "cd is not supported. The working directory is always htdocs/.", startedAt)
            else -> runExternal(command, tokens.drop(1), trimmed, startedAt)
        }
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun runExternal(
        name: String,
        arguments: List<String>,
        commandLine: String,
        startedAt: Long,
    ): TerminalResult {
        if (name !in allowedCommands) {
            return error(commandLine, "Command not allowed: $name. Type 'help' for available commands.", startedAt)
        }
        val resolved = commandResolver.resolve(name)
            ?: return error(
                commandLine,
                "$name is not installed. Import a runtime that provides it from Runtime Manager.",
                startedAt,
            )
        if (!resolved.executable.isFile || !resolved.executable.canExecute()) {
            return error(commandLine, "$name is not executable: ${resolved.executable.absolutePath}", startedAt)
        }
        val process = try {
            ProcessBuilder(listOf(resolved.executable.absolutePath) + resolved.leadingArguments + arguments)
                .directory(documentRoot)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment().putAll(defaultEnvironment())
                    environment().putAll(environmentProvider())
                }
                .start()
        } catch (exception: Exception) {
            return error(commandLine, "Unable to start $name: ${exception.message}", startedAt)
        }

        val output = StringBuilder()
        var truncatedFlag = false
        val reader = scope.async {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length + line.length + 1 <= maxOutputBytes) {
                        output.append(line).append('\n')
                    } else {
                        truncatedFlag = true
                    }
                }
            }
        }
        val finished = runCatching { withTimeoutOrNull(timeoutMs) { reader.await() } }.getOrNull()
        val timedOut = finished == null && reader.isActive
        if (timedOut) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
            reader.cancel()
        }
        process.waitFor(2, TimeUnit.SECONDS)
        val exit = runCatching { process.exitValue() }.getOrNull()
        return TerminalResult(
            command = commandLine,
            output = output.toString(),
            exitCode = exit,
            durationMs = elapsed(startedAt),
            truncated = truncatedFlag,
            isError = timedOut || (exit != null && exit != 0),
            timedOut = timedOut,
        )
    }

    private fun list(arguments: List<String>, commandLine: String, startedAt: Long): TerminalResult {
        val target = resolveDocumentPath(arguments.firstOrNull())
            ?: return error(commandLine, "Path escapes htdocs", startedAt)
        if (!target.exists()) return error(commandLine, "No such file or directory", startedAt)
        if (!target.isDirectory) return error(commandLine, "Not a directory", startedAt)
        val entries = target.listFiles().orEmpty()
            .filter { PathGuard.isInside(documentRoot, it) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .joinToString("\n") { file ->
                if (file.isDirectory) "${file.name}/" else "${file.name}  (${file.length()} bytes)"
            }
        return builtin(commandLine, entries.ifEmpty { "(empty)" }, startedAt)
    }

    private fun cat(arguments: List<String>, commandLine: String, startedAt: Long): TerminalResult {
        val relative = arguments.firstOrNull()
            ?: return error(commandLine, "Usage: cat <file>", startedAt)
        val target = resolveDocumentPath(relative)
            ?: return error(commandLine, "Path escapes htdocs", startedAt)
        if (!target.isFile) return error(commandLine, "Not a file: $relative", startedAt)
        if (target.length() > maxOutputBytes) return error(commandLine, "File is too large to display", startedAt)
        val bytes = target.readBytes()
        if (bytes.take(4096).any { it == 0.toByte() }) {
            return error(commandLine, "Refusing to print a binary file", startedAt)
        }
        return builtin(commandLine, String(bytes, Charsets.UTF_8), startedAt)
    }

    private fun resolveDocumentPath(argument: String?): File? {
        if (argument.isNullOrBlank()) return documentRoot
        return try {
            PathGuard.resolveWithin(documentRoot, argument)
        } catch (_: Exception) {
            null
        }
    }

    private fun builtin(command: String, output: String, startedAt: Long): TerminalResult =
        TerminalResult(command, output, 0, elapsed(startedAt))

    private fun error(command: String, message: String, startedAt: Long): TerminalResult =
        TerminalResult(command, message, 1, elapsed(startedAt), isError = true)

    private fun elapsed(startedAt: Long) = System.currentTimeMillis() - startedAt

    private fun defaultEnvironment(): Map<String, String> = mapOf(
        "PATH" to "/system/bin:/system/xbin",
        "HOME" to documentRoot.absolutePath,
        "TMPDIR" to documentRoot.absolutePath,
        "LANG" to "C.UTF-8",
    )

    private fun helpText(): String = """
        PocketServer terminal
        Built-ins:
          help            Show this help
          pwd             Print the htdocs path
          ls [path]       List a directory under htdocs
          cat <file>      Print a text file under htdocs
          clear           Clear the terminal
        Runtime commands (when installed):
          ${allowedCommands.sorted().joinToString(", ")}
        The terminal does not execute shell syntax. Arguments are passed directly to programs.
    """.trimIndent()

    companion object {
        val DEFAULT_ALLOWED_COMMANDS: Set<String> = setOf(
            "php",
            "composer",
            "node",
            "npm",
            "npx",
            "python",
            "python3",
            "pip",
            "pip3",
            "git",
            "mysql",
            "mariadb",
            "mysqladmin",
            "mariadb-admin",
            "httpd",
            "nginx",
            "php-fpm",
            "mysqld",
            "mariadbd",
        )
    }
}
