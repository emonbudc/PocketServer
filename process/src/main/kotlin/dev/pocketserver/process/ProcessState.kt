package dev.pocketserver.process

enum class ProcessState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED,
}

data class ProcessSnapshot(
    val id: String,
    val state: ProcessState,
    val pid: Long? = null,
    val exitCode: Int? = null,
    val error: String? = null,
    val startedAtEpochMs: Long? = null,
    val stoppedAtEpochMs: Long? = null,
)
