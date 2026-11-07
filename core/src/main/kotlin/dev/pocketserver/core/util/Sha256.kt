package dev.pocketserver.core.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun hashFile(file: File): String = file.inputStream().use(::hashStream)

    fun hashStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun hashBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun matches(expectedHex: String, actualHex: String): Boolean {
        val expected = expectedHex.trim().lowercase()
        val actual = actualHex.trim().lowercase()
        if (expected.length != 64 || actual.length != 64) return false
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    fun isHexDigest(value: String): Boolean = HEX.matches(value.trim())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val HEX = Regex("[0-9a-fA-F]{64}")
}
