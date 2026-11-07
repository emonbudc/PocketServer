package dev.pocketserver.core.util

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

data class PortCheck(
    val port: Int,
    val valid: Boolean,
    val available: Boolean,
    val detail: String,
) {
    val ok: Boolean get() = valid && available
}

object PortValidator {
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    const val DEFAULT_HTTP_PORT = 8080
    const val DEFAULT_PHP_FPM_PORT = 9000

    fun validate(port: Int): String? = when {
        port !in MIN_PORT..MAX_PORT ->
            "Port must be between $MIN_PORT and $MAX_PORT (Android apps cannot bind privileged ports)."
        else -> null
    }

    fun isAvailable(port: Int, host: String = "127.0.0.1"): Boolean {
        if (validate(port) != null) return false
        return try {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName(host), port))
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    fun check(port: Int, host: String = "127.0.0.1"): PortCheck {
        val problem = validate(port)
        if (problem != null) return PortCheck(port, false, false, problem)
        val free = isAvailable(port, host)
        return PortCheck(
            port = port,
            valid = true,
            available = free,
            detail = if (free) "Available on $host" else "Already in use on $host",
        )
    }
}
