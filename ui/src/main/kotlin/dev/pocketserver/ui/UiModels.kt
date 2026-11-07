package dev.pocketserver.ui

import dev.pocketserver.core.util.PortCheck

data class TerminalLine(
    val command: String? = null,
    val text: String,
    val isError: Boolean = false,
    val isMeta: Boolean = false,
)

data class StorageUsage(
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    val runtimeBytes: Long = 0,
)

data class RuntimeUiState(
    val deviceAbis: List<String> = emptyList(),
    val probeOutput: String? = null,
    val probeRunning: Boolean = false,
    val probeError: String? = null,
)

data class FilePreview(
    val relativePath: String,
    val content: String,
    val isBinary: Boolean = false,
)

data class PortStatus(
    val checks: Map<Int, PortCheck> = emptyMap(),
    val conflicts: List<String> = emptyList(),
)

