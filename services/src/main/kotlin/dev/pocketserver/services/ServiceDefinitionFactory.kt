package dev.pocketserver.services

import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.model.RuntimeManifest
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.util.PathGuard
import dev.pocketserver.runtime.RuntimeInstaller
import java.io.File

data class ServiceLayout(
    val configRoot: File,
    val htdocs: File,
    val logsRoot: File,
    val dataRoot: File,
    val tempRoot: File,
)

class ServiceDefinitionFactory(
    private val layout: ServiceLayout,
    private val runtimeInstaller: RuntimeInstaller,
    private val settings: () -> AppSettings,
) {
    fun create(): Map<ServiceKind, ServiceDefinition> {
        val current = settings()
        val php = phpDefinition(current)
        val apache = webDefinition(ServiceKind.APACHE, current, php)
        val nginx = webDefinition(ServiceKind.NGINX, current, php)
        return mapOf(
            ServiceKind.PHP_FPM to php,
            ServiceKind.MARIADB to databaseDefinition(ServiceKind.MARIADB, current),
            ServiceKind.MYSQL to databaseDefinition(ServiceKind.MYSQL, current),
            ServiceKind.APACHE to apache,
            ServiceKind.NGINX to nginx,
        )
    }

    private fun phpDefinition(settings: AppSettings): ServiceDefinition {
        val runtime = runtimeInstaller.findInstalled("php")
        val config = configFile("php/php-fpm.conf")
        if (runtime == null) {
            return unavailable(ServiceKind.PHP_FPM, settings, "PHP runtime is not installed. Import an ARM64 PHP bundle.")
        }
        val entrypoint = runtimeInstaller.activeEntrypoint("php", "php-fpm")
            ?: return unavailable(
                ServiceKind.PHP_FPM,
                settings,
                "PHP runtime ${runtime.manifest.version} does not contain the 'php-fpm' entrypoint.",
            )
        if (!config.isFile) {
            return unavailable(
                ServiceKind.PHP_FPM,
                settings,
                "PHP-FPM configuration is missing: ${config.absolutePath}",
            )
        }
        return ServiceDefinition(
            kind = ServiceKind.PHP_FPM,
            enabled = true,
            port = settings.phpFpmPort,
            launch = ServiceLaunch(
                executable = entrypoint,
                arguments = listOf("-F", "-y", config.absolutePath),
                workingDirectory = layout.htdocs,
                environment = runtimeEnvironment(runtime.installPath),
                logFile = logFile(ServiceKind.PHP_FPM),
            ),
            healthCheck = HealthCheck.Tcp(port = settings.phpFpmPort),
            validationArguments = listOf("-t", "-y", config.absolutePath),
            configFiles = listOf(config),
        )
    }

    private fun webDefinition(
        kind: ServiceKind,
        settings: AppSettings,
        php: ServiceDefinition,
    ): ServiceDefinition {
        val selected = settings.webServer == kind
        val runtimeId = if (kind == ServiceKind.APACHE) "apache" else "nginx"
        val entrypointName = if (kind == ServiceKind.APACHE) "httpd" else "nginx"
        val configName = if (kind == ServiceKind.APACHE) "apache/httpd.conf" else "nginx/nginx.conf"
        val runtime = runtimeInstaller.findInstalled(runtimeId)
        val config = configFile(configName)
        if (!selected) {
            return unavailable(kind, settings, "Not selected as the active web server")
        }
        if (runtime == null) {
            return unavailable(
                kind,
                settings,
                "${kind.displayName} runtime is not installed. Import an ARM64 ${kind.displayName} bundle.",
            )
        }
        val entrypoint = runtimeInstaller.activeEntrypoint(runtimeId, entrypointName)
            ?: return unavailable(
                kind,
                settings,
                "${kind.displayName} runtime ${runtime.manifest.version} does not contain the '$entrypointName' entrypoint.",
            )
        if (!config.isFile) {
            return unavailable(
                kind,
                settings,
                "${kind.displayName} configuration is missing: ${config.absolutePath}",
            )
        }
        val dependencies = if (php.isAvailable) setOf(ServiceKind.PHP_FPM) else emptySet()
        val arguments = if (kind == ServiceKind.APACHE) {
            listOf("-f", config.absolutePath, "-DFOREGROUND")
        } else {
            listOf("-c", config.absolutePath, "-g", "daemon off;")
        }
        return ServiceDefinition(
            kind = kind,
            enabled = true,
            port = settings.httpPort,
            dependencies = dependencies,
            launch = ServiceLaunch(
                executable = entrypoint,
                arguments = arguments,
                workingDirectory = layout.htdocs,
                environment = runtimeEnvironment(runtime.installPath),
                logFile = logFile(kind),
            ),
            healthCheck = HealthCheck.Http(port = settings.httpPort),
            validationArguments = if (kind == ServiceKind.APACHE) {
                listOf("-t", "-f", config.absolutePath)
            } else {
                listOf("-t", "-c", config.absolutePath)
            },
            configFiles = listOf(config),
        )
    }

    private fun databaseDefinition(kind: ServiceKind, settings: AppSettings): ServiceDefinition {
        val runtimeId = if (kind == ServiceKind.MARIADB) "mariadb" else "mysql"
        val entrypointName = if (kind == ServiceKind.MARIADB) "mariadbd" else "mysqld"
        val configName = if (kind == ServiceKind.MARIADB) "database/mariadb.cnf" else "database/mysql.cnf"
        val port = settings.portFor(kind)
        val runtime = runtimeInstaller.findInstalled(runtimeId)
        val config = configFile(configName)
        if (runtime == null) {
            return unavailable(
                kind,
                settings,
                "${kind.displayName} runtime is not installed. Import an ARM64 ${kind.displayName} bundle.",
            )
        }
        val entrypoint = runtimeInstaller.activeEntrypoint(runtimeId, entrypointName)
            ?: return unavailable(
                kind,
                settings,
                "${kind.displayName} runtime ${runtime.manifest.version} does not contain the '$entrypointName' entrypoint.",
            )
        if (!config.isFile) {
            return unavailable(
                kind,
                settings,
                "${kind.displayName} configuration is missing: ${config.absolutePath}",
            )
        }
        return ServiceDefinition(
            kind = kind,
            enabled = true,
            port = port,
            launch = ServiceLaunch(
                executable = entrypoint,
                arguments = listOf("--defaults-file=${config.absolutePath}"),
                workingDirectory = layout.htdocs,
                environment = runtimeEnvironment(runtime.installPath),
                logFile = logFile(kind),
            ),
            healthCheck = HealthCheck.Tcp(port = port),
            configFiles = listOf(config),
        )
    }

    private fun unavailable(kind: ServiceKind, settings: AppSettings, reason: String): ServiceDefinition =
        ServiceDefinition(
            kind = kind,
            enabled = kind == ServiceKind.PHP_FPM || kind.isDatabase || settings.webServer == kind,
            port = settings.portFor(kind),
            unavailableReason = reason,
        )

    private fun configFile(relative: String): File = PathGuard.resolveWithin(layout.configRoot, relative)

    private fun logFile(kind: ServiceKind): File =
        PathGuard.resolveWithin(layout.logsRoot, "${kind.id}/service.log")

    private fun runtimeEnvironment(installPath: String): Map<String, String> {
        val root = File(installPath)
        val binDirs = listOf(File(root, "bin"), File(root, "sbin"))
            .filter { it.isDirectory }
            .joinToString(":") { it.absolutePath }
        val libDirs = listOf(File(root, "lib"), File(root, "lib64"))
            .filter { it.isDirectory }
            .joinToString(":") { it.absolutePath }
        return buildMap {
            put("PATH", listOf(binDirs, "/system/bin", "/system/xbin").filter { it.isNotBlank() }.joinToString(":"))
            put("LD_LIBRARY_PATH", listOf(libDirs, "/system/lib64").filter { it.isNotBlank() }.joinToString(":"))
            put("HOME", layout.htdocs.absolutePath)
            put("TMPDIR", layout.tempRoot.absolutePath)
            put("LANG", "C.UTF-8")
        }
    }

    companion object {
        val REQUIRED_ENTRYPOINTS: Map<String, Set<String>> = mapOf(
            "php" to setOf("php", "php-fpm"),
            "apache" to setOf("httpd"),
            "nginx" to setOf("nginx"),
            "mariadb" to setOf("mariadbd"),
            "mysql" to setOf("mysqld"),
        )

        fun describeRuntime(manifest: RuntimeManifest): String =
            "${manifest.id} ${manifest.version} (${manifest.abi})"
    }
}
