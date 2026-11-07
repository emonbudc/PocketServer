package dev.pocketserver.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeCatalogTest {
    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun parsesValidCatalog() {
        val catalog = RuntimeCatalogCodec.decode(
            """
            {
              "formatVersion": 1,
              "bundles": [
                {
                  "id": "php",
                  "version": "8.4.1",
                  "url": "https://github.com/emonbudc/PocketServer/releases/download/runtime-bundles-v1/php-8.5.12-1-arm64.zip",
                  "sha256": "$sha",
                  "sizeBytes": 4096
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, catalog.bundles.size)
        assertEquals("php", catalog.bundles.single().id)
    }

    @Test
    fun acceptsEmptyCatalog() {
        val catalog = RuntimeCatalogCodec.decode("""{"formatVersion":1,"bundles":[]}""")
        assertTrue(catalog.bundles.isEmpty())
    }

    @Test
    fun rejectsInsecureUrl() {
        assertFailsWith<RuntimeCatalogException> {
            RuntimeCatalogCodec.decode(
                """{"formatVersion":1,"bundles":[{"id":"php","version":"1","url":"http://github.com/emonbudc/PocketServer/releases/download/runtime-bundles-v1/php-8.5.12-1-arm64.zip","sha256":"$sha"}]}""",
            )
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        assertFailsWith<RuntimeCatalogException> {
            RuntimeCatalogCodec.decode(
                """
                {"formatVersion":1,"bundles":[
                  {"id":"php","version":"1","url":"https://github.com/emonbudc/PocketServer/releases/download/runtime-bundles-v1/php-8.5.12-1-arm64.zip","sha256":"$sha"},
                  {"id":"php","version":"2","url":"https://github.com/emonbudc/PocketServer/releases/download/runtime-bundles-v1/nginx-1.31.8-arm64.zip","sha256":"$sha"}
                ]}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun rejectsBadDigest() {
        assertFailsWith<RuntimeCatalogException> {
            RuntimeCatalogCodec.decode(
                """{"formatVersion":1,"bundles":[{"id":"php","version":"1","url":"https://github.com/emonbudc/PocketServer/releases/download/runtime-bundles-v1/php-8.5.12-1-arm64.zip","sha256":"abc"}]}""",
            )
        }
    }
}
