package dev.pocketserver.core.util

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortValidatorTest {
    @Test
    fun rejectsPrivilegedAndOutOfRangePorts() {
        assertNotNull(PortValidator.validate(80))
        assertNotNull(PortValidator.validate(0))
        assertNotNull(PortValidator.validate(65536))
        assertNull(PortValidator.validate(8080))
    }

    @Test
    fun detectsBoundPortAsUnavailable() {
        ServerSocket(0).use { socket ->
            val port = socket.localPort
            val check = PortValidator.check(port)
            assertTrue(check.valid)
            assertFalse(check.available)
            assertTrue(check.detail.contains("in use"))
        }
    }

    @Test
    fun reportsInvalidPortDetail() {
        val check = PortValidator.check(22)
        assertFalse(check.valid)
        assertFalse(check.ok)
        assertEquals(22, check.port)
    }
}
