package dev.pocketserver.core.model

enum class ServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
    UNKNOWN,
    ;

    fun canTransitionTo(next: ServiceState): Boolean = when (this) {
        STOPPED -> next == STARTING || next == UNKNOWN
        STARTING -> next == RUNNING || next == FAILED || next == STOPPING
        RUNNING -> next == STOPPING || next == FAILED || next == UNKNOWN
        STOPPING -> next == STOPPED || next == FAILED
        FAILED -> next == STARTING || next == STOPPED || next == UNKNOWN
        UNKNOWN -> next == STARTING || next == STOPPED || next == FAILED
    }
}

data class ServiceSnapshot(
    val kind: ServiceKind,
    val state: ServiceState = ServiceState.UNKNOWN,
    val pid: Long? = null,
    val port: Int = kind.defaultPort,
    val healthDetail: String? = null,
    val error: String? = null,
    val startedAtEpochMs: Long? = null,
) {
    val isBusy: Boolean
        get() = state == ServiceState.STARTING || state == ServiceState.STOPPING
}
