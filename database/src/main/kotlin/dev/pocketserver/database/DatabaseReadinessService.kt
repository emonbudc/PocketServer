package dev.pocketserver.database

import dev.pocketserver.core.util.PathGuard
import dev.pocketserver.runtime.RuntimeInstaller
import java.io.File

data class DatabaseReadiness(
    val engine: DatabaseEngine,
    val runtimeInstalled: Boolean,
    val runtimeVersion: String?,
    val serverExecutablePresent: Boolean,
    val configurationPresent: Boolean,
    val dataDirectoryPresent: Boolean,
    val executablePath: String?,
    val configurationPath: String,
    val dataDirectoryPath: String,
    val issues: List<String>,
) {
    val ready: Boolean get() = issues.isEmpty()
}

class DatabaseReadinessService(
    private val runtimeInstaller: RuntimeInstaller,
    private val configRoot: File,
    private val dataRoot: File,
) {
    fun check(engine: DatabaseEngine): DatabaseReadiness {
        val installed = runtimeInstaller.findInstalled(engine.runtimeId)
        val entrypoint = runtimeInstaller.activeEntrypoint(engine.runtimeId, engine.serverEntrypoint)
        val config = PathGuard.resolveWithin(configRoot, engine.configRelativePath)
        val dataDirectory = PathGuard.resolveWithin(dataRoot, engine.dataDirectoryName)
        return DatabaseReadiness(
            engine = engine,
            runtimeInstalled = installed != null,
            runtimeVersion = installed?.manifest?.version,
            serverExecutablePresent = entrypoint?.isFile == true,
            configurationPresent = config.isFile,
            dataDirectoryPresent = dataDirectory.isDirectory,
            executablePath = entrypoint?.absolutePath,
            configurationPath = config.absolutePath,
            dataDirectoryPath = dataDirectory.absolutePath,
            issues = buildList {
                if (installed == null) add("${engine.runtimeId} runtime is not installed")
                if (entrypoint == null) add("${engine.serverEntrypoint} entrypoint is missing")
                if (!config.isFile) add("Server configuration is missing: ${config.absolutePath}")
                if (!dataDirectory.isDirectory) add("Data directory has not been prepared")
            },
        )
    }

    fun prepareDataDirectory(engine: DatabaseEngine): File {
        val dataDirectory = PathGuard.resolveWithin(dataRoot, engine.dataDirectoryName)
        if (dataDirectory.isDirectory) return dataDirectory
        require(dataDirectory.mkdirs()) { "Unable to create ${dataDirectory.absolutePath}" }
        return dataDirectory
    }
}
