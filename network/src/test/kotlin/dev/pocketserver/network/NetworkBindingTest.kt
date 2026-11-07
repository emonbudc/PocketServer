package dev.pocketserver.network

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkBindingTest {
    @Test
    fun bindsLoopbackUnlessLanIsExplicitlyEnabled() {
        assertEquals("127.0.0.1", NetworkBinding.bindHost(false))
        assertEquals("0.0.0.0", NetworkBinding.bindHost(true))
    }

    @Test
    fun buildsLocalUrls() {
        assertEquals("http://127.0.0.1:8080/", NetworkBinding.localUrl(8080))
        assertEquals("http://127.0.0.1:8080/phpmyadmin/", NetworkBinding.localUrl(8080, "phpmyadmin/"))
        assertEquals("http://127.0.0.1:8080/phpmyadmin/", NetworkBinding.localUrl(8080, "/phpmyadmin/"))
    }
}
