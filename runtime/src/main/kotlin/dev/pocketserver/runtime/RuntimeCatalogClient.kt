package dev.pocketserver.runtime

import dev.pocketserver.core.util.Sha256
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class RuntimeCatalogClient(
    private val maxCatalogBytes: Long = 1024 * 1024,
    private val maxBundleBytes: Long = RuntimeBundleDescriptor.MAX_ARCHIVE_BYTES,
) {
    fun fetchCatalog(url: String): RuntimeCatalog {
        val connection = open(url)
        try {
            val response = connection.responseCode
            if (response !in 200..299) {
                throw RuntimeCatalogException("Catalog request failed with HTTP $response")
            }
            val declared = connection.contentLengthLong
            if (declared > maxCatalogBytes) {
                throw RuntimeCatalogException("Catalog is too large (${declared} bytes)")
            }
            val bytes = connection.inputStream.use { readLimited(it, maxCatalogBytes, "Catalog") }
            return RuntimeCatalogCodec.decode(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    fun downloadBundle(descriptor: RuntimeBundleDescriptor, destination: File): File {
        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, "${destination.name}.part")
        part.delete()
        val connection = open(descriptor.url)
        var total = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val response = connection.responseCode
            if (response !in 200..299) {
                throw RuntimeCatalogException(
                    "Download for '${descriptor.id}' failed with HTTP $response",
                )
            }
            val declared = connection.contentLengthLong
            if (declared > maxBundleBytes) {
                throw RuntimeCatalogException("Bundle '${descriptor.id}' exceeds the download size limit")
            }
            if (declared > 0 && descriptor.sizeBytes > 0 && declared != descriptor.sizeBytes) {
                throw RuntimeCatalogException(
                    "Bundle '${descriptor.id}' size mismatch: catalog=${descriptor.sizeBytes}, server=$declared",
                )
            }

            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBundleBytes) {
                            throw RuntimeCatalogException(
                                "Bundle '${descriptor.id}' exceeds the download size limit",
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
        } catch (exception: Exception) {
            part.delete()
            throw exception
        } finally {
            connection.disconnect()
        }

        if (descriptor.sizeBytes > 0 && total != descriptor.sizeBytes) {
            part.delete()
            throw RuntimeCatalogException(
                "Bundle '${descriptor.id}' size mismatch: expected ${descriptor.sizeBytes}, got $total",
            )
        }
        val actual = digestHex(digest.digest())
        if (!Sha256.matches(descriptor.sha256, actual)) {
            part.delete()
            throw RuntimeCatalogException(
                "Bundle '${descriptor.id}' SHA-256 mismatch: expected ${descriptor.sha256}, got $actual",
            )
        }
        destination.delete()
        if (!part.renameTo(destination)) {
            part.delete()
            throw RuntimeCatalogException("Unable to finalize downloaded bundle '${descriptor.id}'")
        }
        return destination
    }

    private fun open(url: String): HttpURLConnection {
        val parsed = runCatching { URI(url) }.getOrNull()
        if (parsed == null || parsed.scheme != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
            throw RuntimeCatalogException("Refusing non-HTTPS catalog URL")
        }
        val connection = parsed.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "PocketServer/0.1")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.connect()
        if (connection.url.protocol != "https") {
            connection.disconnect()
            throw RuntimeCatalogException("Refusing redirect to a non-HTTPS URL")
        }
        return connection
    }

    private fun readLimited(input: InputStream, limit: Long, label: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw RuntimeCatalogException("$label exceeds $limit bytes")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun digestHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { append("%02x".format(it)) }
    }
}
