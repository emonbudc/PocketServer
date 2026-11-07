package dev.pocketserver.process

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManagedProcessTest {
    private val shell = File("/bin/sh")
    private val workDir = Files.createTempDirectory("ps-process").toFile()
    private val logFile = File(workDir, "process.log")

    @Test
    fun runsAndCapturesOutput() = runBlocking {
        val process = ManagedProcess(spec("echo"))
        process.start()
        val snapshot = awaitTerminal(process)
        assertEquals(ProcessState.STOPPED, snapshot.state)
        assertTrue(logFile.readText().contains("pocketserver-output"))
        process.close()
    }

    @Test
    fun capturesStderrAndNonZeroExit() = runBlocking {
        val process = ManagedProcess(spec("fail", "-c", "echo oops >&2; exit 7"))
        process.start()
        val snapshot = awaitTerminal(process)
        assertEquals(ProcessState.FAILED, snapshot.state)
        assertEquals(7, snapshot.exitCode)
        assertTrue(logFile.readText().contains("oops"))
        process.close()
    }

    @Test
    fun stopsLongRunningProcess() = runBlocking {
        val process = ManagedProcess(
            spec("sleep", "-c", "sleep 30", environment = mapOf("PATH" to "/usr/bin:/bin:/usr/local/bin")),
        )
        process.start()
        val snapshot = process.stop(2_000)
        assertEquals(ProcessState.STOPPED, snapshot.state)
        assertTrue(process.state.value == ProcessState.STOPPED)
        process.close()
    }

    @Test
    fun rejectsMissingExecutable() {
        assertFailsWith<IllegalArgumentException> {
            spec("echo").copy(executable = File(workDir, "missing"))
        }
    }

    private suspend fun awaitTerminal(process: ManagedProcess, timeoutMs: Long = 5_000): ProcessSnapshot {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = process.snapshot.value
            if (current.state == ProcessState.STOPPED || current.state == ProcessState.FAILED) {
                return current
            }
            delay(20)
        }
        return process.snapshot.value
    }

    private fun spec(
        commandName: String,
        vararg args: String,
        environment: Map<String, String> = emptyMap(),
    ): ProcessSpec = ProcessSpec(
        id = "test-$commandName",
        executable = shell,
        arguments = if (commandName == "echo") {
            listOf("-c", "echo pocketserver-output")
        } else if (args.firstOrNull() == "-c") {
            args.toList()
        } else {
            listOf("-c", "printf done")
        },
        workingDirectory = workDir,
        environment = environment,
        logFile = logFile,
    )
}
