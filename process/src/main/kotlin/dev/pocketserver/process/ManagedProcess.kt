package dev.pocketserver.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ManagedProcess(
    private val spec: ProcessSpec,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {
    private val _state = MutableStateFlow(ProcessState.IDLE)
    val state: StateFlow<ProcessState> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow(ProcessSnapshot(spec.id, ProcessState.IDLE))
    val snapshot: StateFlow<ProcessSnapshot> = _snapshot.asStateFlow()

    private val _output = MutableSharedFlow<String>(
        replay = 64,
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val output: SharedFlow<String> = _output.asSharedFlow()

    private val lifecycleLock = Any()
    private val stopping = AtomicBoolean(false)
    private var process: Process? = null
    private var pumpJob: Job? = null

    fun start() {
        synchronized(lifecycleLock) {
            check(process == null) { "Process ${spec.id} has already been started" }
            stopping.set(false)
            transition(ProcessState.STARTING, startedAt = System.currentTimeMillis())
            val builder = ProcessBuilder(spec.commandLine())
                .directory(spec.workingDirectory)
                .redirectErrorStream(true)
            builder.environment().clear()
            builder.environment().putAll(defaultEnvironment())
            builder.environment().putAll(spec.environment)
            val started = try {
                builder.start()
            } catch (exception: Exception) {
                transition(
                    ProcessState.FAILED,
                    error = "Unable to start ${spec.executable.name}: ${exception.message}",
                    stoppedAt = System.currentTimeMillis(),
                )
                throw ProcessStartException("Unable to start ${spec.executable.name}", exception)
            }
            process = started
            transition(ProcessState.RUNNING, pid = started.pid(), startedAt = _snapshot.value.startedAtEpochMs)
            val pump = scope.launch { pumpOutput(started) }
            pumpJob = pump
            scope.launch {
                val exit = runCatching {
                    started.waitFor()
                    started.exitValue()
                }.getOrNull()
                pump.join()
                onExit(exit)
            }
        }
    }

    suspend fun stop(timeoutMs: Long = 5_000): ProcessSnapshot = withContext(Dispatchers.IO) {
        val current = synchronized(lifecycleLock) {
            val running = process ?: return@synchronized null
            if (!running.isAlive) {
                process = null
                return@synchronized null
            }
            if (stopping.compareAndSet(false, true)) {
                transition(ProcessState.STOPPING)
            }
            running
        }
        if (current != null) {
            current.destroy()
            val exited = current.waitFor(timeoutMs.coerceIn(0, 30_000), TimeUnit.MILLISECONDS)
            if (!exited) {
                current.destroyForcibly()
                current.waitFor(3, TimeUnit.SECONDS)
            }
        }
        awaitTerminalState()
        snapshot.value
    }

    override fun close() {
        stopping.set(true)
        process?.let {
            if (it.isAlive) it.destroyForcibly()
        }
        pumpJob?.cancel()
        scope.cancel()
    }

    private suspend fun awaitTerminalState() {
        repeat(50) {
            if (_state.value == ProcessState.STOPPED || _state.value == ProcessState.FAILED) return
            kotlinx.coroutines.delay(20)
        }
    }

    private fun onExit(exitCode: Int?) {
        synchronized(lifecycleLock) {
            process = null
            val failed = !stopping.get() && exitCode != null && exitCode != 0
            transition(
                if (failed) ProcessState.FAILED else ProcessState.STOPPED,
                exitCode = exitCode,
                stoppedAt = System.currentTimeMillis(),
                error = if (failed) "Process exited with code $exitCode" else null,
            )
        }
    }

    private fun pumpOutput(running: Process) {
        try {
            spec.logFile.parentFile?.mkdirs()
            PrintWriter(FileWriter(spec.logFile, true), true).use { writer ->
                running.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        writer.println("${Instant.now()} $line")
                        _output.tryEmit(line)
                    }
                }
            }
        } catch (_: Exception) {
            // The stream closes when the process is terminated; lifecycle state is authoritative.
        }
    }

    private fun transition(
        state: ProcessState,
        pid: Long? = _snapshot.value.pid,
        exitCode: Int? = _snapshot.value.exitCode,
        error: String? = _snapshot.value.error,
        startedAt: Long? = _snapshot.value.startedAtEpochMs,
        stoppedAt: Long? = _snapshot.value.stoppedAtEpochMs,
    ) {
        _state.value = state
        _snapshot.value = ProcessSnapshot(
            id = spec.id,
            state = state,
            pid = pid,
            exitCode = exitCode,
            error = error,
            startedAtEpochMs = startedAt,
            stoppedAtEpochMs = stoppedAt,
        )
    }

    private fun defaultEnvironment(): Map<String, String> = mapOf(
        "PATH" to "/system/bin:/system/xbin:${spec.workingDirectory.absolutePath}",
        "HOME" to spec.workingDirectory.absolutePath,
        "TMPDIR" to spec.workingDirectory.absolutePath,
        "LANG" to "C.UTF-8",
    )

    companion object {
        private const val EXIT_POLL_SECONDS = 1L
    }
}

class ProcessStartException(message: String, cause: Throwable? = null) : Exception(message, cause)
