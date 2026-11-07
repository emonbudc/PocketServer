package dev.pocketserver.core.config

import dev.pocketserver.core.model.ServiceKind

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val httpPort: Int = 8080,
    val phpFpmPort: Int = 9000,
    val mariadbPort: Int = 3306,
    val mysqlPort: Int = 3307,
    val webServer: ServiceKind = ServiceKind.APACHE,
    val lanAccessEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoRestartAfterProcessDeath: Boolean = true,
    val logLineLimit: Int = 500,
) {
    fun portFor(kind: ServiceKind): Int = when (kind) {
        ServiceKind.APACHE, ServiceKind.NGINX -> httpPort
        ServiceKind.PHP_FPM -> phpFpmPort
        ServiceKind.MARIADB -> mariadbPort
        ServiceKind.MYSQL -> mysqlPort
    }
}
