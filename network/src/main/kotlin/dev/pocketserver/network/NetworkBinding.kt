package dev.pocketserver.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkBinding {
    const val LOOPBACK = "127.0.0.1"
    const val ALL_INTERFACES = "0.0.0.0"

    fun bindHost(lanEnabled: Boolean): String = if (lanEnabled) ALL_INTERFACES else LOOPBACK

    fun localUrl(port: Int, path: String = "/"): String =
        "http://$LOOPBACK:$port${if (path.startsWith("/")) path else "/$path"}"

    fun publicUrl(host: String, port: Int, path: String = "/"): String =
        "http://$host:$port${if (path.startsWith("/")) path else "/$path"}"

    fun lanIpv4Addresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.let(Collections::list)
                .orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
