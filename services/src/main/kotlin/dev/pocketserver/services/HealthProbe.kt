package dev.pocketserver.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class HealthResult(
    val healthy: Boolean,
    val detail: String,
)

fun interface HealthProbe {
    suspend fun probe(definition: ServiceDefinition): HealthResult
}

class NetworkHealthProbe(
    private val connectTimeoutMs: Int = 800,
    private val readTimeoutMs: Int = 1_500,
) : HealthProbe {
    override suspend fun probe(definition: ServiceDefinition): HealthResult {
        val check = definition.healthCheck
            ?: return HealthResult(false, "No health check configured")
        return withContext(Dispatchers.IO) {
            when (check) {
                is HealthCheck.Tcp -> probeTcp(check)
                is HealthCheck.Http -> probeHttp(check)
            }
        }
    }

    private fun probeTcp(check: HealthCheck.Tcp): HealthResult = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(check.host, check.port), connectTimeoutMs)
        }
        HealthResult(true, "TCP ${check.host}:${check.port} is accepting connections")
    } catch (exception: Exception) {
        HealthResult(false, "TCP ${check.host}:${check.port} unavailable: ${exception.message}")
    }

    private fun probeHttp(check: HealthCheck.Http): HealthResult {
        val url = URL("http", check.host, check.port, check.path)
        val connection = try {
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PocketServer-HealthCheck/1.0")
            }
        } catch (exception: Exception) {
            return HealthResult(false, "HTTP health check setup failed: ${exception.message}")
        }
        return try {
            val code = connection.responseCode
            val healthy = code in 100..599 &&
                (check.acceptServerError || code < 500)
            HealthResult(healthy, "HTTP $code from ${check.host}:${check.port}${check.path}")
        } catch (exception: Exception) {
            HealthResult(false, "HTTP ${check.host}:${check.port} unavailable: ${exception.message}")
        } finally {
            connection.disconnect()
        }
    }
}
