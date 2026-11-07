package dev.pocketserver.services

import dev.pocketserver.core.model.ServiceKind
import java.io.File

data class ServiceLaunch(
    val executable: File,
    val arguments: List<String> = emptyList(),
    val workingDirectory: File,
    val environment: Map<String, String> = emptyMap(),
    val logFile: File,
)

sealed interface HealthCheck {
    val host: String
    val port: Int

    data class Tcp(
        override val host: String = "127.0.0.1",
        override val port: Int,
    ) : HealthCheck

    data class Http(
        override val host: String = "127.0.0.1",
        override val port: Int,
        val path: String = "/",
        val acceptServerError: Boolean = false,
    ) : HealthCheck
}

data class ServiceDefinition(
    val kind: ServiceKind,
    val enabled: Boolean,
    val port: Int,
    val dependencies: Set<ServiceKind> = emptySet(),
    val launch: ServiceLaunch? = null,
    val unavailableReason: String? = null,
    val healthCheck: HealthCheck? = null,
    val validationArguments: List<String>? = null,
    val configFiles: List<File> = emptyList(),
    val healthTimeoutMs: Long = 12_000,
    val healthIntervalMs: Long = 250,
) {
    val isAvailable: Boolean
        get() = enabled && launch != null && unavailableReason == null

    fun initialUnavailableReason(): String? = when {
        unavailableReason != null -> unavailableReason
        !enabled -> "Not enabled in Settings"
        launch == null -> "Runtime is not installed"
        else -> null
    }
}

class ServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)
