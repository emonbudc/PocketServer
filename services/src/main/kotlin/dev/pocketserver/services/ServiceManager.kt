package dev.pocketserver.services

import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceSnapshot
import dev.pocketserver.core.model.ServiceState
import dev.pocketserver.process.ManagedProcess
import dev.pocketserver.process.ProcessSpec
import dev.pocketserver.process.ProcessState
import dev.pocketserver.process.ProcessSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

class ServiceManager(
    private val definitionsProvider: () -> Map<ServiceKind, ServiceDefinition>,
    private val supervisor: ProcessSupervisor = ProcessSupervisor(),
    private val healthProbe: HealthProbe = NetworkHealthProbe(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val _definitions = MutableStateFlow(definitionsProvider())
    val definitions: StateFlow<Map<ServiceKind, ServiceDefinition>> = _definitions.asStateFlow()

    private val _snapshots = MutableStateFlow<Map<ServiceKind, ServiceSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<ServiceKind, ServiceSnapshot>> = _snapshots.asStateFlow()

    private val monitors = mutableMapOf<ServiceKind, Job>()

    init {
        refresh()
    }

    fun refresh() {
        val definitions = definitionsProvider()
        _definitions.value = definitions
        _snapshots.update { current ->
            ServiceKind.entries.associateWith { kind ->
                val existing = current[kind] ?: ServiceSnapshot(kind)
                val definition = definitions[kind]
                if (existing.state.isActive) {
                    existing.copy(port = definition?.port ?: existing.port)
                } else {
                    existing.copy(
                        port = definition?.port ?: kind.defaultPort,
                        error = definition?.initialUnavailableReason(),
                    )
                }
            }
        }
    }

    fun definition(kind: ServiceKind): ServiceDefinition? = _definitions.value[kind]

    fun snapshot(kind: ServiceKind): ServiceSnapshot =
        _snapshots.value[kind] ?: ServiceSnapshot(kind)

    suspend fun start(kind: ServiceKind): ServiceSnapshot = mutex.withLock {
        startLocked(kind, mutableSetOf())
        snapshot(kind)
    }

    suspend fun stop(kind: ServiceKind): ServiceSnapshot = mutex.withLock {
        stopLocked(kind)
        snapshot(kind)
    }

    suspend fun restart(kind: ServiceKind): ServiceSnapshot {
        stop(kind)
        return start(kind)
    }

    suspend fun startAll(): Map<ServiceKind, ServiceSnapshot> {
        val ordered = orderedKinds()
        ordered.forEach { kind ->
            val definition = _definitions.value[kind] ?: return@forEach
            if (!definition.isAvailable) return@forEach
            try {
                start(kind)
            } catch (_: Exception) {
                // The failure is reflected in the service snapshot; continue starting independent services.
            }
        }
        return _snapshots.value
    }

    suspend fun stopAll() {
        val ordered = orderedKinds().reversed()
        ordered.forEach { kind ->
            runCatching { stop(kind) }
        }
    }

    suspend fun shutdown() {
        stopAll()
        monitors.values.forEach { it.cancel() }
        monitors.clear()
        supervisor.shutdown()
        scope.cancel()
    }

    fun logFile(kind: ServiceKind): File? = _definitions.value[kind]?.launch?.logFile

    private suspend fun startLocked(kind: ServiceKind, stack: MutableSet<ServiceKind>) {
        if (!stack.add(kind)) {
            throw ServiceException("Dependency cycle detected at ${kind.displayName}")
        }
        val definition = _definitions.value[kind]
            ?: throw ServiceException("No definition for ${kind.displayName}")
        if (!definition.enabled) {
            throw ServiceException("${kind.displayName} is not enabled: ${definition.initialUnavailableReason()}")
        }
        if (definition.unavailableReason != null) {
            setState(kind, ServiceState.FAILED, error = definition.unavailableReason)
            throw ServiceException(definition.unavailableReason)
        }
        val launch = definition.launch
            ?: run {
                val failure = "${kind.displayName} runtime is not installed"
                setState(kind, ServiceState.FAILED, error = failure)
                throw ServiceException(failure)
            }
        val current = snapshot(kind)
        if (current.state == ServiceState.RUNNING) return
        if (current.isBusy) throw ServiceException("${kind.displayName} is already ${current.state.name.lowercase()}")

        if (!launch.executable.isFile) {
            val failure = "${kind.displayName} executable is missing: ${launch.executable.absolutePath}"
            setState(kind, ServiceState.FAILED, error = failure)
            throw ServiceException(failure)
        }
        if (!launch.executable.canExecute()) {
            val failure = "${kind.displayName} executable is not executable: ${launch.executable.name}"
            setState(kind, ServiceState.FAILED, error = failure)
            throw ServiceException(failure)
        }
        definition.configFiles.forEach { config ->
            if (!config.isFile) {
                val failure = "${kind.displayName} configuration is missing: ${config.absolutePath}"
                setState(kind, ServiceState.FAILED, error = failure)
                throw ServiceException(failure)
            }
        }

        definition.dependencies.sortedBy { it.startupOrder }.forEach { dependency ->
            if (snapshot(dependency).state != ServiceState.RUNNING) {
                startLocked(dependency, stack)
            }
        }

        setState(kind, ServiceState.STARTING, error = null)
        validateConfiguration(definition)?.let { failure ->
            setState(kind, ServiceState.FAILED, error = failure)
            throw ServiceException(failure)
        }

        val spec = ProcessSpec(
            id = kind.id,
            executable = launch.executable,
            arguments = launch.arguments,
            workingDirectory = launch.workingDirectory,
            environment = launch.environment,
            logFile = launch.logFile,
        )
        val managed = try {
            supervisor.start(spec)
        } catch (exception: Exception) {
            val failure = "${kind.displayName} failed to start: ${exception.message}"
            setState(kind, ServiceState.FAILED, error = failure)
            throw ServiceException(failure, exception)
        }

        setState(kind, ServiceState.STARTING, pid = managed.snapshot.value.pid)
        monitor(kind, managed)
        val health = awaitHealthy(definition, managed)
        if (!health.healthy) {
            runCatching { managed.stop(3_000) }
            monitors.remove(kind)?.cancel()
            setState(kind, ServiceState.FAILED, error = health.detail)
            throw ServiceException("${kind.displayName} did not pass its health check: ${health.detail}")
        }
        setState(
            kind,
            ServiceState.RUNNING,
            pid = managed.snapshot.value.pid,
            health = health.detail,
            startedAt = System.currentTimeMillis(),
            error = null,
        )
    }

    private suspend fun stopLocked(kind: ServiceKind) {
        val dependents = ServiceKind.entries.filter { candidate ->
            candidate != kind &&
                snapshot(candidate).state.isActive &&
                _definitions.value[candidate]?.dependencies?.contains(kind) == true
        }
        dependents.forEach { stopLocked(it) }

        val current = snapshot(kind)
        if (current.state == ServiceState.STOPPED || current.state == ServiceState.UNKNOWN) return
        setState(kind, ServiceState.STOPPING)
        try {
            supervisor.stop(kind.id)
        } catch (exception: Exception) {
            setState(kind, ServiceState.FAILED, error = "Failed to stop ${kind.displayName}: ${exception.message}")
            return
        }
        monitors.remove(kind)?.cancel()
        val definition = _definitions.value[kind]
        setState(
            kind,
            ServiceState.STOPPED,
            pid = null,
            health = null,
            error = definition?.initialUnavailableReason(),
        )
    }

    private suspend fun awaitHealthy(
        definition: ServiceDefinition,
        managed: ManagedProcess,
    ): HealthResult {
        val check = definition.healthCheck
            ?: return HealthResult(true, "No health check configured")
        val deadline = System.currentTimeMillis() + definition.healthTimeoutMs
        var last = HealthResult(false, "Health check did not run")
        while (System.currentTimeMillis() < deadline) {
            if (managed.state.value == ProcessState.FAILED || managed.state.value == ProcessState.STOPPED) {
                return HealthResult(
                    false,
                    "Process exited before becoming healthy (exit ${managed.snapshot.value.exitCode})",
                )
            }
            val hasPort = when (check) {
                is HealthCheck.Tcp -> check.port
                is HealthCheck.Http -> check.port
            }
            if (hasPort <= 0) return HealthResult(false, "Invalid health check port")
            last = healthProbe.probe(definition)
            if (last.healthy) return last
            delay(definition.healthIntervalMs)
        }
        return last.copy(healthy = false, detail = "Timed out: ${last.detail}")
    }

    private fun monitor(kind: ServiceKind, managed: ManagedProcess) {
        monitors.remove(kind)?.cancel()
        monitors[kind] = scope.launch {
            managed.state.collect { processState ->
                if (processState == ProcessState.FAILED || processState == ProcessState.STOPPED) {
                    val current = snapshot(kind)
                    if (current.state == ServiceState.RUNNING) {
                        setState(
                            kind,
                            ServiceState.FAILED,
                            pid = null,
                            health = null,
                            error = "${kind.displayName} process exited unexpectedly " +
                                "(code ${managed.snapshot.value.exitCode})",
                        )
                    }
                }
            }
        }
    }

    private suspend fun validateConfiguration(definition: ServiceDefinition): String? {
        val arguments = definition.validationArguments ?: return null
        val executable = definition.launch?.executable ?: return "Executable is missing"
        val process = try {
            ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .directory(definition.launch.workingDirectory)
                .redirectErrorStream(true)
                .apply { environment().putAll(definition.launch.environment) }
                .start()
        } catch (exception: Exception) {
            return "Unable to run configuration validation: ${exception.message}"
        }
        val result = withTimeoutOrNull(15_000) {
            withContext(Dispatchers.IO) {
                val output = process.inputStream.bufferedReader().use { reader ->
                    reader.readText().take(4_000)
                }
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(3, TimeUnit.SECONDS)
                }
                val exit = runCatching { process.exitValue() }.getOrDefault(-1)
                exit to output
            }
        }
        if (result == null) {
            process.destroyForcibly()
            return "Configuration validation timed out"
        }
        val (exit, output) = result
        return if (exit != 0) {
            "Configuration validation failed (exit $exit): ${output.ifBlank { "no diagnostics" }}"
        } else {
            null
        }
    }

    private fun orderedKinds(): List<ServiceKind> =
        _definitions.value.values
            .sortedBy { it.kind.startupOrder }
            .map { it.kind }

    private fun setState(
        kind: ServiceKind,
        state: ServiceState,
        pid: Long? = snapshot(kind).pid,
        health: String? = snapshot(kind).healthDetail,
        error: String? = snapshot(kind).error,
        startedAt: Long? = snapshot(kind).startedAtEpochMs,
    ) {
        _snapshots.update { current ->
            val base = current[kind] ?: ServiceSnapshot(kind)
            current + (kind to base.copy(
                state = state,
                pid = pid,
                healthDetail = health,
                error = error,
                startedAtEpochMs = startedAt,
            ))
        }
    }

    private val ServiceState.isActive: Boolean
        get() = this == ServiceState.STARTING || this == ServiceState.RUNNING || this == ServiceState.STOPPING
}
