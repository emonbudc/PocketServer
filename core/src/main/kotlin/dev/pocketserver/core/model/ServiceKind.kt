package dev.pocketserver.core.model

enum class ServiceKind(
    val id: String,
    val displayName: String,
    val defaultPort: Int,
    val startupOrder: Int,
) {
    PHP_FPM("php-fpm", "PHP-FPM", 9000, 10),
    MARIADB("mariadb", "MariaDB", 3306, 20),
    MYSQL("mysql", "MySQL", 3307, 21),
    NGINX("nginx", "Nginx", 8080, 30),
    APACHE("apache", "Apache", 8080, 31),
    ;

    val isDatabase: Boolean
        get() = this == MARIADB || this == MYSQL

    val isWebServer: Boolean
        get() = this == NGINX || this == APACHE

    companion object {
        fun fromId(id: String): ServiceKind? = entries.firstOrNull { it.id == id }
    }
}
