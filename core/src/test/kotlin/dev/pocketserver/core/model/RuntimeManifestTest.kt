package dev.pocketserver.core.model

import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeManifestTest {
    @Test
    fun acceptsValidManifest() {
        val manifest = RuntimeManifest(
            id = "php",
            version = "8.4.1",
            entrypoints = mapOf("php" to "bin/php", "php-fpm" to "sbin/php-fpm"),
        )
        assertTrue(manifest.validationErrors().isEmpty())
    }

    @Test
    fun rejectsUnsafeEntrypointAndAbi() {
        val manifest = RuntimeManifest(
            id = "php",
            version = "8.4.1",
            abi = "x86_64",
            entrypoints = mapOf("php" to "../../bin/php"),
        )
        val errors = manifest.validationErrors()
        assertTrue(errors.size >= 2)
    }

    @Test
    fun rejectsMalformedIdentifiers() {
        val manifest = RuntimeManifest(id = "PHP Runtime", version = "../8.4")
        assertTrue(manifest.validationErrors().isNotEmpty())
    }
}
