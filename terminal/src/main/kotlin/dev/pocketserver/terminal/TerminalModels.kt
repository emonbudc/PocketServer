package dev.pocketserver.terminal

import java.io.File

data class TerminalExecutable(
    val executable: File,
    val leadingArguments: List<String> = emptyList(),
)

fun interface TerminalCommandResolver {
    fun resolve(name: String): TerminalExecutable?
}

data class TerminalResult(
    val command: String,
    val output: String,
    val exitCode: Int?,
    val durationMs: Long,
    val truncated: Boolean = false,
    val isError: Boolean = false,
    val clearScreen: Boolean = false,
    val timedOut: Boolean = false,
)
