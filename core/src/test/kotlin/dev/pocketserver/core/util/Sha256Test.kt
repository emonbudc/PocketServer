package dev.pocketserver.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256Test {
    @Test
    fun hashesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hashBytes("abc".toByteArray()),
        )
    }

    @Test
    fun hashesFile() {
        val file = File.createTempFile("ps-hash", ".txt").apply {
            writeText("abc")
            deleteOnExit()
        }
        assertEquals(Sha256.hashBytes("abc".toByteArray()), Sha256.hashFile(file))
    }

    @Test
    fun comparesDigests() {
        val digest = Sha256.hashBytes("payload".toByteArray())
        assertTrue(Sha256.matches(digest.uppercase(), digest))
        assertFalse(Sha256.matches("00".repeat(32), digest))
        assertFalse(Sha256.matches("not-a-digest", digest))
    }
}
