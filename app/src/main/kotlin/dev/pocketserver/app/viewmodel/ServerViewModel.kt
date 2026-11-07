package dev.pocketserver.app.viewmodel

import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketserver.app.di.AppContainer
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.config.ThemeMode
import dev.pocketserver.core.model.InstalledRuntime
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceSnapshot
import dev.pocketserver.core.util.PortCheck
import dev.pocketserver.core.util.PortValidator
import dev.pocketserver.services.ServiceDefinition
import dev.pocketserver.ui.PortStatus
import dev.pocketserver.ui.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.config.settings
    val services: StateFlow<Map<ServiceKind, ServiceSnapshot>> = container.serviceManager.snapshots
    val definitions: StateFlow<Map<ServiceKind, ServiceDefinition>> = container.serviceManager.definitions

    private val _runtimes = MutableStateFlow<List<InstalledRuntime>>(emptyList())
    val runtimes: StateFlow<List<InstalledRuntime>> = _runtimes.asStateFlow()

    private val _storage = MutableStateFlow(StorageUsage())
    val storage: StateFlow<StorageUsage> = _storage.asStateFlow()

    private val _portStatus = MutableStateFlow(PortStatus())
    val portStatus: StateFlow<PortStatus> = _portStatus.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        container.serviceManager.refresh()
        loadRuntimes()
        loadStorage()
        checkPorts()
    }

    fun loadRuntimes() {
        viewModelScope.launch(Dispatchers.IO) {
            _runtimes.value = runCatching { container.runtimeInstaller.listInstalled() }.getOrDefault(emptyList())
        }
    }

    private fun loadStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _storage.value = runCatching {
                val stat = StatFs(container.paths.root.absolutePath)
                val runtimeBytes = container.paths.packages.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                StorageUsage(
                    freeBytes = stat.availableBytes,
                    totalBytes = stat.totalBytes,
                    runtimeBytes = runtimeBytes,
                )
            }.getOrDefault(StorageUsage())
        }
    }

    fun checkPorts() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.config.snapshot()
            val assigned = listOf(
                "HTTP" to current.httpPort,
                "PHP-FPM" to current.phpFpmPort,
                "MariaDB" to current.mariadbPort,
                "MySQL" to current.mysqlPort,
            )
            val checks: Map<Int, PortCheck> = assigned.map { it.second }.distinct().associateWith { port ->
                PortValidator.check(port)
            }
            val conflicts = assigned.groupBy { it.second }
                .filterValues { it.size > 1 }
                .map { (port, names) ->
                    "Port $port is assigned to ${names.joinToString(" and ") { it.first }}"
                }
            _portStatus.value = PortStatus(checks = checks, conflicts = conflicts)
        }
    }

    fun applyPorts(httpPort: Int, phpFpmPort: Int, mariadbPort: Int, mysqlPort: Int) {
        try {
            container.config.setPorts(httpPort, phpFpmPort, mariadbPort, mysqlPort)
            container.serviceManager.refresh()
            checkPorts()
            _message.value = "Ports saved. Restart running services and update runtime configs to match."
        } catch (exception: Exception) {
            _message.value = "Ports were not saved: ${exception.message}"
        }
    }

    fun setWebServer(kind: ServiceKind) {
        container.config.setWebServer(kind)
        container.serviceManager.refresh()
        _message.value = "${kind.displayName} selected. Restart web services to apply."
    }

    fun setTheme(mode: ThemeMode) {
        container.config.setThemeMode(mode)
    }

    fun setLanAccess(enabled: Boolean) {
        container.config.setLanAccessEnabled(enabled)
        container.serviceManager.refresh()
        _message.value = if (enabled) {
            "LAN access enabled in PocketServer. Runtime configs must bind 0.0.0.0; restart services after updating them."
        } else {
            "LAN access disabled. Services remain loopback-only where configured."
        }
    }

    fun setAutoRestart(enabled: Boolean) {
        container.config.setAutoRestart(enabled)
    }

    fun startAll() = container.serverController.startAll()

    fun stopAll() = container.serverController.stopAll()

    fun start(kind: ServiceKind) = container.serverController.start(kind)

    fun stop(kind: ServiceKind) = container.serverController.stop(kind)

    fun restart(kind: ServiceKind) = container.serverController.restart(kind)

    fun clearMessage() {
        _message.value = null
    }
}
