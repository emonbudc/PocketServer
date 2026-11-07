package dev.pocketserver.runtime

import dev.pocketserver.core.util.Sha256
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeInstallerTest {
    private val root = Files.createTempDirectory("ps-runtime-test").toFile()
    private val dirs = RuntimeDirectories(
        root = root,
        packages = File(root, "packages"),
        temp = File(root, "temp"),
    )
    private val installer = RuntimeInstaller(dirs)

    @Test
    fun installsVerifiesAndActivatesRuntime() {
        val archive = createArchive(
            manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","abi":"arm64-v8a","entrypoints":{"probe":"bin/probe"}}""",
            files = mapOf("bin/probe" to aarch64Elf()),
        )
        val checksum = Sha256.hashFile(archive)
        val result = installer.install(archive, checksum)

        assertTrue(result.installed.verified)
        assertEquals("probe", result.installed.manifest.id)
        assertTrue(result.installed.installPath.contains("packages/probe/1.0.0"))
        assertTrue(File(result.installed.installPath, "bin/probe").canExecute())
        assertNotNull(installer.activeEntrypoint("probe", "probe"))
        assertEquals(1, installer.listInstalled().size)
    }

    @Test
    fun rejectsChecksumMismatch() {
        val archive = createArchive(
            manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","entrypoints":{"probe":"bin/probe"}}""",
            files = mapOf("bin/probe" to aarch64Elf()),
        )
        assertFailsWith<RuntimeInstallException> {
            installer.install(archive, "0".repeat(64))
        }
        assertTrue(installer.listInstalled().isEmpty())
    }

    @Test
    fun rejectsZipSlipEntry() {
        val archive = createArchive(
            manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","entrypoints":{"probe":"bin/probe"}}""",
            files = mapOf(
                "bin/probe" to aarch64Elf(),
                "../escape.txt" to "nope".toByteArray(),
            ),
        )
        assertFailsWith<RuntimeInstallException> { installer.install(archive) }
        assertFalse(File(root.parentFile, "escape.txt").exists())
    }

    @Test
    fun rejectsNonAarch64Entrypoint() {
        val archive = createArchive(
            manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","entrypoints":{"probe":"bin/probe"}}""",
            files = mapOf("bin/probe" to "#!/system/bin/sh\n".toByteArray()),
        )
        assertFailsWith<RuntimeInstallException> { installer.install(archive) }
    }

    @Test
    fun rejectsDuplicateVersionWithoutOverwrite() {
        val manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","entrypoints":{"probe":"bin/probe"}}"""
        val first = createArchive(manifest, mapOf("bin/probe" to aarch64Elf()), "first.zip")
        val second = createArchive(manifest, mapOf("bin/probe" to aarch64Elf()), "second.zip")
        installer.install(first)
        assertFailsWith<RuntimeInstallException> { installer.install(second) }
        installer.install(second, overwrite = true)
    }

    @Test
    fun uninstallsOnlyInsidePackages() {
        val archive = createArchive(
            manifest = """{"formatVersion":1,"id":"probe","version":"1.0.0","entrypoints":{"probe":"bin/probe"}}""",
            files = mapOf("bin/probe" to aarch64Elf()),
        )
        installer.install(archive)
        assertTrue(installer.uninstall("probe"))
        assertTrue(installer.listInstalled().isEmpty())
        assertFalse(installer.uninstall("../packages"))
    }

    private fun createArchive(
        manifest: String,
        files: Map<String, ByteArray>,
        name: String = "runtime-${System.nanoTime()}.zip",
    ): File {
        val archive = File(root.parentFile, name)
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun aarch64Elf(): ByteArray {
        val header = ByteArray(64)
        header[0] = 0x7F
        header[1] = 'E'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 2
        header[5] = 1
        header[6] = 1
        header[16] = 3
        header[18] = 183.toByte()
        return header
    }
}
