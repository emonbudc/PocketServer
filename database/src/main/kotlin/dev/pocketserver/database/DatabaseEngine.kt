package dev.pocketserver.database

enum class DatabaseEngine(
    val id: String,
    val displayName: String,
    val runtimeId: String,
    val serverEntrypoint: String,
    val defaultPort: Int,
    val dataDirectoryName: String,
    val configRelativePath: String,
) {
    MARIADB(
        id = "mariadb",
        displayName = "MariaDB",
        runtimeId = "mariadb",
        serverEntrypoint = "mariadbd",
        defaultPort = 3306,
        dataDirectoryName = "mariadb",
        configRelativePath = "database/mariadb.cnf",
    ),
    MYSQL(
        id = "mysql",
        displayName = "Oracle MySQL",
        runtimeId = "mysql",
        serverEntrypoint = "mysqld",
        defaultPort = 3307,
        dataDirectoryName = "mysql",
        configRelativePath = "database/mysql.cnf",
    ),
}
