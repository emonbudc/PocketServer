package dev.pocketserver.terminal

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalSessionTest {
    private val root = Files.createTempDirectory("ps-terminal").toFile()

    @Test
    fun executesBuiltinsWithinHtdocs() = runBlocking {
        File(root, "index.php").writeText("<?php echo 'ok';")
        File(root, "assets").mkdirs()
        val terminal = TerminalSession(root, TerminalCommandResolver { null })
        try {
            val pwd = terminal.execute("pwd")
            assertEquals(root.absolutePath, pwd.output)
            assertFalse(pwd.isError)

            val listing = terminal.execute("ls")
            assertTrue(listing.output.contains("index.php"))
            assertTrue(listing.output.contains("assets/"))

            val cat = terminal.execute("cat index.php")
            assertTrue(cat.output.contains("<?php"))
        } finally {
            terminal.close()
        }
    }

    @Test
    fun rejectsTraversal() = runBlocking {
        val terminal = TerminalSession(root, TerminalCommandResolver { null })
        try {
            val result = terminal.execute("cat ../secret.txt")
            assertTrue(result.isError)
            assertTrue(result.output.contains("escapes"))
        } finally {
            terminal.close()
        }
    }

    @Test
    fun rejectsUnknownAndUninstalledCommands() = runBlocking {
        val terminal = TerminalSession(root, TerminalCommandResolver { null })
        try {
            assertTrue(terminal.execute("rm -rf /").isError)
            assertTrue(terminal.execute("unknown").isError)
            assertTrue(terminal.execute("php -v").isError)
            assertTrue(terminal.execute("php -v").output.contains("not installed"))
        } finally {
            terminal.close()
        }
    }

    @Test
    fun runsResolvedCommandWithoutShellInterpretation() = runBlocking {
        val terminal = TerminalSession(
            documentRoot = root,
            commandResolver = { name ->
                if (name == "echo") TerminalExecutable(File("/bin/echo")) else null
            },
            allowedCommands = setOf("echo"),
            environmentProvider = { emptyMap() },
        )
        try {
            val result = terminal.execute("echo hello; whoami")
            assertEquals(0, result.exitCode)
            assertEquals("hello; whoami", result.output.trim())
        } finally {
            terminal.close()
        }
    }

    @Test
    fun recordsHistory() = runBlocking {
        val terminal = TerminalSession(root, TerminalCommandResolver { null })
        try {
            terminal.execute("pwd")
            terminal.execute("help")
            assertEquals(listOf("pwd", "help"), terminal.history())
        } finally {
            terminal.close()
        }
    }
}
