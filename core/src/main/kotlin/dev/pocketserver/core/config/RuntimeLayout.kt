package dev.pocketserver.core.config

object RuntimeLayout {
    const val ROOT = "pocketserver"
    const val RUNTIME = "runtime"
    const val PACKAGES = "runtime/packages"
    const val BIN = "bin"
    const val CONFIG = "config"
    const val HTDOCS = "htdocs"
    const val DATA = "data"
    const val LOGS = "logs"
    const val BACKUPS = "backups"
    const val TEMP = "temp"

    val directories: List<String> = listOf(
        RUNTIME,
        PACKAGES,
        BIN,
        CONFIG,
        "$CONFIG/apache",
        "$CONFIG/nginx",
        "$CONFIG/php",
        "$CONFIG/database",
        HTDOCS,
        DATA,
        "$DATA/mariadb",
        "$DATA/mysql",
        LOGS,
        "$LOGS/app",
        "$LOGS/apache",
        "$LOGS/nginx",
        "$LOGS/php",
        "$LOGS/mariadb",
        "$LOGS/mysql",
        BACKUPS,
        TEMP,
    )

    val serviceLogDirectories: Map<String, String> = mapOf(
        "apache" to "$LOGS/apache",
        "nginx" to "$LOGS/nginx",
        "php-fpm" to "$LOGS/php",
        "mariadb" to "$LOGS/mariadb",
        "mysql" to "$LOGS/mysql",
    )
}
