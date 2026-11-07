package dev.pocketserver.app.di

import android.content.Context
import android.os.Build
import dev.pocketserver.app.BuildConfig
import dev.pocketserver.app.NativeProbe
import dev.pocketserver.app.RuntimeCommandRegistry
import dev.pocketserver.app.service.ServerController
import dev.pocketserver.database.DatabaseReadinessService
import dev.pocketserver.runtime.RuntimeDirectories
import dev.pocketserver.runtime.RuntimeInstaller
import dev.pocketserver.services.ServiceDefinitionFactory
import dev.pocketserver.services.ServiceLayout
import dev.pocketserver.services.ServiceManager
import dev.pocketserver.storage.AppLogger
import dev.pocketserver.storage.ConfigStore
import dev.pocketserver.storage.LogStore
import dev.pocketserver.storage.PocketServerPaths
import dev.pocketserver.storage.ProjectStore
import dev.pocketserver.storage.SecretStore
import dev.pocketserver.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {
    val paths = PocketServerPaths(context)
    val logger = AppLogger(paths)
    val config = ConfigStore(context)
    val secrets = SecretStore(context)
    val logs = LogStore(paths)
    val projects = ProjectStore(paths)

    val runtimeInstaller = RuntimeInstaller(
        RuntimeDirectories(
            root = paths.root,
            packages = paths.packages,
            temp = paths.temp,
        ),
    )

    private val definitionFactory = ServiceDefinitionFactory(
        layout = ServiceLayout(
            configRoot = paths.config,
            htdocs = paths.htdocs,
            logsRoot = paths.logs,
            dataRoot = paths.data,
            tempRoot = paths.temp,
        ),
        runtimeInstaller = runtimeInstaller,
        settings = { config.snapshot() },
    )

    val serviceManager = ServiceManager(definitionsProvider = { definitionFactory.create() })
    val databaseReadiness = DatabaseReadinessService(runtimeInstaller, paths.config, paths.data)
    val commandRegistry = RuntimeCommandRegistry(runtimeInstaller)
    val terminal = TerminalSession(
        documentRoot = paths.htdocs,
        commandResolver = commandRegistry,
        environmentProvider = { commandRegistry.environment() },
    )
    val nativeProbe = NativeProbe(context, paths, logger)
    val serverController = ServerController(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize() {
        paths.ensureLayout()
        logger.info(
            "application",
            "PocketServer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started; " +
                "device ABI=${Build.SUPPORTED_ABIS.joinToString(",")}",
        )
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) {
            logger.warn(
                "application",
                "arm64-v8a is not in Build.SUPPORTED_ABIS; native runtime execution will fail on this device",
            )
        }
        serviceManager.refresh()
    }
}
