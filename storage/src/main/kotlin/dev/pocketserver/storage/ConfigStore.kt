package dev.pocketserver.storage

import android.content.Context
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.config.ThemeMode
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.util.PortValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun snapshot(): AppSettings = mutableSettings.value

    fun setHttpPort(port: Int) {
        require(PortValidator.validate(port) == null) { "Invalid HTTP port: $port" }
        prefs.edit().putInt(KEY_HTTP_PORT, port).apply()
        refresh()
    }

    fun setPhpFpmPort(port: Int) {
        require(PortValidator.validate(port) == null) { "Invalid PHP-FPM port: $port" }
        prefs.edit().putInt(KEY_PHP_FPM_PORT, port).apply()
        refresh()
    }

    fun setDatabasePort(kind: ServiceKind, port: Int) {
        require(kind.isDatabase) { "Not a database engine: $kind" }
        require(PortValidator.validate(port) == null) { "Invalid database port: $port" }
        prefs.edit().putInt(dbKey(kind), port).apply()
        refresh()
    }

    fun setPorts(httpPort: Int, phpFpmPort: Int, mariadbPort: Int, mysqlPort: Int) {
        listOf(httpPort, phpFpmPort, mariadbPort, mysqlPort).forEach { port ->
            require(PortValidator.validate(port) == null) { "Invalid port: $port" }
        }
        require(setOf(httpPort, phpFpmPort, mariadbPort, mysqlPort).size == 4) {
            "Each service must use a distinct port"
        }
        prefs.edit()
            .putInt(KEY_HTTP_PORT, httpPort)
            .putInt(KEY_PHP_FPM_PORT, phpFpmPort)
            .putInt(dbKey(ServiceKind.MARIADB), mariadbPort)
            .putInt(dbKey(ServiceKind.MYSQL), mysqlPort)
            .apply()
        refresh()
    }

    fun setDesiredRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_DESIRED_RUNNING, running).apply()
    }

    fun desiredRunning(): Boolean = prefs.getBoolean(KEY_DESIRED_RUNNING, false)

    fun setWebServer(kind: ServiceKind) {
        require(kind.isWebServer) { "Not a web server: $kind" }
        prefs.edit().putString(KEY_WEB_SERVER, kind.id).apply()
        refresh()
    }

    fun setLanAccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LAN_ENABLED, enabled).apply()
        refresh()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        refresh()
    }

    fun setAutoRestart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESTART, enabled).apply()
        refresh()
    }

    fun refresh() {
        mutableSettings.value = read()
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        val webServer = prefs.getString(KEY_WEB_SERVER, defaults.webServer.id)
            ?.let(ServiceKind::fromId)
            ?.takeIf { it.isWebServer }
            ?: defaults.webServer
        val theme = prefs.getString(KEY_THEME, defaults.themeMode.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: defaults.themeMode
        return AppSettings(
            httpPort = prefs.getInt(KEY_HTTP_PORT, defaults.httpPort),
            phpFpmPort = prefs.getInt(KEY_PHP_FPM_PORT, defaults.phpFpmPort),
            mariadbPort = prefs.getInt(dbKey(ServiceKind.MARIADB), defaults.mariadbPort),
            mysqlPort = prefs.getInt(dbKey(ServiceKind.MYSQL), defaults.mysqlPort),
            webServer = webServer,
            lanAccessEnabled = prefs.getBoolean(KEY_LAN_ENABLED, defaults.lanAccessEnabled),
            themeMode = theme,
            autoRestartAfterProcessDeath = prefs.getBoolean(KEY_AUTO_RESTART, defaults.autoRestartAfterProcessDeath),
        )
    }

    private fun dbKey(kind: ServiceKind) = "db_port_${kind.id}"

    companion object {
        private const val PREFS_NAME = "pocketserver-settings"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_PHP_FPM_PORT = "php_fpm_port"
        private const val KEY_WEB_SERVER = "web_server"
        private const val KEY_LAN_ENABLED = "lan_enabled"
        private const val KEY_THEME = "theme"
        private const val KEY_AUTO_RESTART = "auto_restart"
        private const val KEY_DESIRED_RUNNING = "desired_running"
    }
}
