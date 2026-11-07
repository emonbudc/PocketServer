package dev.pocketserver.process

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class ProcessSupervisor {
    private val mutex = Mutex()
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    suspend fun start(spec: ProcessSpec): ManagedProcess = mutex.withLock {
        val existing = processes[spec.id]
        if (existing != null && existing.state.value.isActive) {
            throw ProcessStartException("Process ${spec.id} is already running")
        }
        if (existing != null) existing.close()
        val managed = ManagedProcess(spec)
        try {
            managed.start()
        } catch (exception: Exception) {
            managed.close()
            throw exception
        }
        processes[spec.id] = managed
        managed
    }

    suspend fun stop(id: String, timeoutMs: Long = 5_000): ProcessSnapshot? = mutex.withLock {
        processes[id]?.stop(timeoutMs)
    }

    suspend fun stopAll(timeoutMs: Long = 5_000) {
        val current = mutex.withLock { processes.values.toList() }
        current.forEach { runCatching { it.stop(timeoutMs) } }
    }

    fun get(id: String): ManagedProcess? = processes[id]

    fun snapshots(): List<ProcessSnapshot> = processes.values.map { it.snapshot.value }

    suspend fun shutdown() {
        stopAll()
        val current = mutex.withLock {
            val copy = processes.values.toList()
            processes.clear()
            copy
        }
        current.forEach { it.close() }
    }

    private val ProcessState.isActive: Boolean
        get() = this == ProcessState.STARTING || this == ProcessState.RUNNING || this == ProcessState.STOPPING
}
