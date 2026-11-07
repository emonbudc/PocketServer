package dev.pocketserver.core.util

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathGuardTest {
    @Test
    fun acceptsNormalRelativePaths() {
        assertTrue(PathGuard.isValidRelativePath("htdocs/index.php"))
        assertTrue(PathGuard.isValidRelativePath("config/php/php.ini"))
        assertTrue(PathGuard.isValidRelativePath("file with spaces.txt"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        listOf(
            "../etc/passwd",
            "htdocs/../../secret",
            "/data/data",
            "C:/windows",
            "~/.ssh",
            "htdocs\\..\\secret",
            "",
            ".",
            "a//b",
        ).forEach { assertFalse(PathGuard.isValidRelativePath(it), "should reject '$it'") }
    }

    @Test
    fun resolveStaysInsideRoot() {
        val root = Files.createTempDirectory("ps-root").toFile()
        val resolved = PathGuard.resolveWithin(root, "htdocs/a.txt")
        assertEquals(File(root, "htdocs/a.txt").canonicalFile, resolved)
        assertFailsWith<UnsafePathException> { PathGuard.resolveWithin(root, "../escape") }
    }

    @Test
    fun resolveRejectsSymlinkEscape() {
        val root = Files.createTempDirectory("ps-root").toFile()
        val outside = Files.createTempDirectory("ps-outside").toFile()
        val link = File(root, "link")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        }
        assertFailsWith<UnsafePathException> { PathGuard.resolveWithin(root, "link/secret") }
    }
}
