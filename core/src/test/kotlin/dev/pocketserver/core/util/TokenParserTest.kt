package dev.pocketserver.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenParserTest {
    @Test
    fun parsesWhitespaceAndQuotes() {
        val result = TokenParser.parse("php -r \"echo 'hello world';\"")
        assertTrue(result.ok)
        assertEquals(listOf("php", "-r", "echo 'hello world';"), result.tokens)
    }

    @Test
    fun keepsMetacharactersLiteral() {
        val result = TokenParser.parse("echo hello; rm -rf /")
        assertTrue(result.ok)
        assertEquals(listOf("echo", "hello;", "rm", "-rf", "/"), result.tokens)
    }

    @Test
    fun rejectsUnterminatedQuotes() {
        assertFalse(TokenParser.parse("php \"oops").ok)
    }

    @Test
    fun rejectsMultilineInput() {
        assertFalse(TokenParser.parse("ls\npwd").ok)
    }
}
