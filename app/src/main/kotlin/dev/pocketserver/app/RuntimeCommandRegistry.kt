package dev.pocketserver.app

import dev.pocketserver.runtime.RuntimeInstaller
import dev.pocketserver.terminal.TerminalCommandResolver
import dev.pocketserver.terminal.TerminalExecutable
import java.io.File

class RuntimeCommandRegistry(
    private val runtimeInstaller: RuntimeInstaller,
) : TerminalCommandResolver {
    override fun resolve(name: String): TerminalExecutable? {
        if (name == "composer") return resolveComposer()
        ENTRYPOINTS[name].orEmpty().forEach { (runtimeId, entrypoint) ->
            runtimeInstaller.activeEntrypoint(runtimeId, entrypoint)?.let { file ->
                return TerminalExecutable(file)
            }
        }
        if (name !in FALLBACK_NAMES) return null
        runtimeInstaller.listInstalled().forEach { runtime ->
            val root = File(runtime.installPath)
            listOf(File(root, "bin"), File(root, "sbin"), File(root, "usr/bin")).forEach { dir ->
                val candidate = File(dir, name)
                if (candidate.isFile && candidate.canExecute()) {
                    return TerminalExecutable(candidate)
                }
            }
        }
        return null
    }

    fun environment(): Map<String, String> {
        val installed = runtimeInstaller.listInstalled()
        val binPaths = installed.flatMap { runtime ->
            val root = File(runtime.installPath)
            listOf(File(root, "bin"), File(root, "sbin"), File(root, "usr/bin"))
        }.filter { it.isDirectory }.map { it.absolutePath }
        val libPaths = installed.flatMap { runtime ->
            val root = File(runtime.installPath)
            listOf(File(root, "lib"), File(root, "lib64"), File(root, "usr/lib"))
        }.filter { it.isDirectory }.map { it.absolutePath }

        return mapOf(
            "PATH" to (binPaths + listOf("/system/bin", "/system/xbin")).joinToString(":"),
            "LD_LIBRARY_PATH" to (libPaths + listOf("/system/lib64")).joinToString(":"),
            "LANG" to "C.UTF-8",
        )
    }

    private fun resolveComposer(): TerminalExecutable? {
        val php = runtimeInstaller.activeEntrypoint("php", "php") ?: return null
        val phpRuntime = runtimeInstaller.findInstalled("php")
        val candidates = buildList {
            if (phpRuntime != null) {
                val root = File(phpRuntime.installPath)
                add(File(root, "bin/composer.phar"))
                add(File(root, "composer.phar"))
                add(File(root, "bin/composer"))
            }
            val composerRuntime = runtimeInstaller.findInstalled("composer")
            if (composerRuntime != null) {
                val root = File(composerRuntime.installPath)
                add(File(root, "bin/composer.phar"))
                add(File(root, "composer.phar"))
            }
        }
        val phar = candidates.firstOrNull { it.isFile }
        return if (phar != null) TerminalExecutable(php, listOf(phar.absolutePath)) else null
    }

    companion object {
        private val ENTRYPOINTS: Map<String, List<Pair<String, String>>> = mapOf(
            "php" to listOf("php" to "php"),
            "php-fpm" to listOf("php" to "php-fpm"),
            "httpd" to listOf("apache" to "httpd"),
            "nginx" to listOf("nginx" to "nginx"),
            "mariadbd" to listOf("mariadb" to "mariadbd"),
            "mysqld" to listOf("mysql" to "mysqld"),
            "mariadb" to listOf("mariadb" to "mariadb"),
            "mysql" to listOf(
                "mariadb" to "mariadb",
                "mariadb" to "mysql",
                "mysql" to "mysql",
            ),
            "mariadb-admin" to listOf("mariadb" to "mariadb-admin"),
            "mysqladmin" to listOf(
                "mariadb" to "mariadb-admin",
                "mariadb" to "mysqladmin",
                "mysql" to "mysqladmin",
            ),
            "node" to listOf("node" to "node"),
            "npm" to listOf("node" to "npm"),
            "npx" to listOf("node" to "npx"),
            "python" to listOf("python" to "python"),
            "python3" to listOf("python" to "python3"),
            "pip" to listOf("python" to "pip", "python" to "pip3"),
            "pip3" to listOf("python" to "pip3"),
            "git" to listOf("git" to "git"),
        )

        private val FALLBACK_NAMES: Set<String> = setOf(
            "mysql",
            "mariadb",
            "mysqladmin",
            "mariadb-admin",
            "npm",
            "npx",
            "pip",
            "pip3",
            "python",
            "python3",
        )
    }
}
