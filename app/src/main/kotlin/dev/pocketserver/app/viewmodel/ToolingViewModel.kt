package dev.pocketserver.app.viewmodel

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketserver.app.di.AppContainer
import dev.pocketserver.core.model.InstalledRuntime
import dev.pocketserver.database.DatabaseEngine
import dev.pocketserver.database.DatabaseReadiness
import dev.pocketserver.storage.LogFileInfo
import dev.pocketserver.terminal.TerminalResult
import dev.pocketserver.ui.RuntimeUiState
import dev.pocketserver.ui.TerminalLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ToolingViewModel(private val container: AppContainer) : ViewModel() {
    private val _runtimes = MutableStateFlow<List<InstalledRuntime>>(emptyList())
    val runtimes: StateFlow<List<InstalledRuntime>> = _runtimes.asStateFlow()

    private val _probe = MutableStateFlow(RuntimeUiState(deviceAbis = Build.SUPPORTED_ABIS.toList()))
    val probe: StateFlow<RuntimeUiState> = _probe.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _logs = MutableStateFlow<List<LogFileInfo>>(emptyList())
    val logs: StateFlow<List<LogFileInfo>> = _logs.asStateFlow()

    private val _selectedLog = MutableStateFlow<LogFileInfo?>(null)
    val selectedLog: StateFlow<LogFileInfo?> = _selectedLog.asStateFlow()

    private val _logContent = MutableStateFlow("")
    val logContent: StateFlow<String> = _logContent.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(
        listOf(
            TerminalLine(
                text = "PocketServer terminal. Type 'help' for built-ins. External commands use imported runtimes only.",
                isMeta = true,
            ),
        ),
    )
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _terminalRunning = MutableStateFlow(false)
    val terminalRunning: StateFlow<Boolean> = _terminalRunning.asStateFlow()

    private val _databaseReadiness = MutableStateFlow<List<DatabaseReadiness>>(emptyList())
    val databaseReadiness: StateFlow<List<DatabaseReadiness>> = _databaseReadiness.asStateFlow()

    private val _databaseBusy = MutableStateFlow<DatabaseEngine?>(null)
    val databaseBusy: StateFlow<DatabaseEngine?> = _databaseBusy.asStateFlow()

    init {
        refreshRuntimes()
        refreshLogs()
        checkDatabases()
    }

    fun refreshRuntimes() {
        viewModelScope.launch(Dispatchers.IO) {
            _runtimes.value = runCatching { container.runtimeInstaller.listInstalled() }.getOrDefault(emptyList())
        }
    }

    fun importRuntime(uri: Uri, expectedSha256: String?, requireChecksum: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val temp = File(container.paths.temp, "runtime-${UUID.randomUUID()}.zip")
            try {
                val size = copyToFile(uri, temp, MAX_BUNDLE_BYTES)
                val checksum = expectedSha256?.trim()?.lowercase()?.ifBlank { null }
                if (checksum == null && requireChecksum) {
                    error("A SHA-256 checksum is required for this import")
                }
                val result = container.runtimeInstaller.install(
                    archive = temp,
                    expectedSha256 = checksum,
                    requireVerification = requireChecksum,
                )
                val manifest = result.installed.manifest
                _message.value = buildString {
                    append("Installed ${manifest.id} ${manifest.version} (${formatBytes(size)}). ")
                    append(
                        if (result.metadata.verified) {
                            "SHA-256 verified."
                        } else {
                            "No checksum supplied; installed without verification."
                        },
                    )
                }
                _runtimes.value = container.runtimeInstaller.listInstalled()
                refreshDatabaseInternal()
            } catch (exception: Exception) {
                _message.value = "Install failed: ${exception.message ?: exception.javaClass.simpleName}"
            } finally {
                temp.delete()
                _busy.value = false
            }
        }
    }

    fun uninstallRuntime(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching { container.runtimeInstaller.uninstall(id) }
                .onSuccess {
                    _message.value = if (it) "Uninstalled runtime $id" else "Runtime $id was not installed"
                    _runtimes.value = container.runtimeInstaller.listInstalled()
                    refreshDatabaseInternal()
                }
                .onFailure { _message.value = "Uninstall failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun runNativeProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            _probe.update {
                it.copy(
                    deviceAbis = Build.SUPPORTED_ABIS.toList(),
                    probeRunning = true,
                    probeError = null,
                )
            }
            val result = runCatching { container.nativeProbe.run() }
            result.onSuccess { probeResult ->
                _probe.update {
                    it.copy(
                        probeRunning = false,
                        deviceAbis = Build.SUPPORTED_ABIS.toList(),
                        probeOutput = probeResult.output,
                        probeError = if (probeResult.success) null else probeResult.output,
                    )
                }
            }.onFailure { exception ->
                _probe.update {
                    it.copy(probeRunning = false, probeError = exception.message ?: "Native probe failed")
                }
            }
        }
    }

    fun refreshLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = runCatching { container.logs.listLogs() }.getOrDefault(emptyList())
            _logs.value = files
            val selected = _selectedLog.value?.let { current -> files.firstOrNull { it.file == current.file } }
                ?: files.firstOrNull()
            _selectedLog.value = selected
            _logContent.value = selected?.let { readLog(it) }.orEmpty()
        }
    }

    fun selectLog(file: LogFileInfo) {
        _selectedLog.value = file
        viewModelScope.launch(Dispatchers.IO) {
            _logContent.value = readLog(file)
        }
    }

    fun selectServiceLog(serviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = runCatching { container.logs.listLogs() }.getOrDefault(emptyList())
            _logs.value = files
            val match = files.firstOrNull { it.label.startsWith("$serviceId/") }
                ?: files.firstOrNull { it.label.contains(serviceId) }
                ?: files.firstOrNull()
            _selectedLog.value = match
            _logContent.value = match?.let { readLog(it) }.orEmpty()
        }
    }

    fun deleteLog(file: LogFileInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.logs.delete(file.file) }
                .onFailure { _message.value = "Unable to delete log: ${it.message}" }
            if (_selectedLog.value?.file == file.file) {
                _selectedLog.value = null
                _logContent.value = ""
            }
            val files = runCatching { container.logs.listLogs() }.getOrDefault(emptyList())
            _logs.value = files
        }
    }

    fun executeTerminal(input: String) {
        if (input.isBlank()) return
        if (_terminalRunning.value) return
        _terminalRunning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { container.terminal.execute(input) }
            result.onSuccess { terminalResult ->
                if (terminalResult.clearScreen) {
                    _terminalLines.value = emptyList()
                } else {
                    _terminalLines.update { lines -> lines + terminalResult.toUiLines() }
                }
            }.onFailure { exception ->
                _terminalLines.update {
                    it + TerminalLine(
                        command = input.trim(),
                        text = exception.message ?: "Terminal command failed",
                        isError = true,
                    )
                }
            }
            _terminalRunning.value = false
        }
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    fun checkDatabases() {
        viewModelScope.launch(Dispatchers.IO) { refreshDatabaseInternal() }
    }

    fun prepareDatabase(engine: DatabaseEngine) {
        viewModelScope.launch(Dispatchers.IO) {
            _databaseBusy.value = engine
            runCatching {
                container.databaseReadiness.prepareDataDirectory(engine)
                container.databaseReadiness.check(engine)
            }.onSuccess { readiness ->
                _databaseReadiness.value = _databaseReadiness.value.map {
                    if (it.engine == engine) readiness else it
                }
                _message.value = if (readiness.ready) {
                    "${engine.displayName} data directory is ready."
                } else {
                    "${engine.displayName} data directory prepared. Still missing: " +
                        readiness.issues.joinToString("; ")
                }
            }.onFailure {
                _message.value = "Unable to prepare ${engine.displayName}: ${it.message}"
            }
            _databaseBusy.value = null
        }
    }

    fun databasePassword(engine: DatabaseEngine): String? =
        runCatching { container.secrets.databasePassword(engine.id) }.getOrNull()

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun refreshDatabaseInternal() {
        val list = runCatching {
            DatabaseEngine.entries.map { container.databaseReadiness.check(it) }
        }.getOrDefault(emptyList())
        _databaseReadiness.value = list
    }

    private fun readLog(file: LogFileInfo): String =
        runCatching { container.logs.readTail(file.file) }.getOrDefault("")

    private fun copyToFile(uri: Uri, target: File, limit: Long): Long {
        val input = container.context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected bundle")
        input.use { stream ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= limit) { "Bundle exceeds the ${limit / (1024 * 1024)} MiB limit" }
                    output.write(buffer, 0, read)
                }
                output.flush()
                return total
            }
        }
    }

    private fun TerminalResult.toUiLines(): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        val body = output.trimEnd()
        lines += TerminalLine(
            command = command,
            text = if (body.isNotBlank()) body else if (isError) "Command failed" else "Done",
            isError = isError,
        )
        if (timedOut) lines += TerminalLine(text = "[timed out]", isError = true, isMeta = true)
        if (truncated) lines += TerminalLine(text = "[output truncated]", isError = true, isMeta = true)
        if (!timedOut && exitCode != null && exitCode != 0) {
            lines += TerminalLine(text = "[exit $exitCode]", isError = true, isMeta = true)
        }
        return lines
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MiB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        private const val MAX_BUNDLE_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
