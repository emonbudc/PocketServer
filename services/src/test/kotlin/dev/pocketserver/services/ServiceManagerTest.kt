package dev.pocketserver.services

import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceState
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceManagerTest {
    private val root = Files.createTempDirectory("ps-services").toFile()
    private val shell = File("/bin/sh")
    private val healthy = HealthProbe { HealthResult(true, "test probe healthy") }

    @Test
    fun startsOnlyAfterHealthCheckAndStops() = runBlocking {
        val manager = manager(definitions(mapOf(ServiceKind.APACHE to definition(ServiceKind.APACHE))))
        try {
            val running = manager.start(ServiceKind.APACHE)
            assertEquals(ServiceState.RUNNING, running.state)
            assertTrue(running.healthDetail?.contains("healthy") == true)

            val stopped = manager.stop(ServiceKind.APACHE)
            assertEquals(ServiceState.STOPPED, stopped.state)
            assertEquals(null, stopped.pid)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun marksFailureWhenHealthCheckFails() = runBlocking {
        val failing = HealthProbe { HealthResult(false, "not ready") }
        val manager = manager(
            definitions(mapOf(ServiceKind.APACHE to definition(ServiceKind.APACHE))),
            healthProbe = failing,
        )
        try {
            assertFailsWith<ServiceException> { manager.start(ServiceKind.APACHE) }
            assertEquals(ServiceState.FAILED, manager.snapshot(ServiceKind.APACHE).state)
            assertTrue(manager.snapshot(ServiceKind.APACHE).error?.contains("not ready") == true)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun startsDependenciesFirst() = runBlocking {
        val definitions = definitions(
            mapOf(
                ServiceKind.PHP_FPM to definition(ServiceKind.PHP_FPM),
                ServiceKind.APACHE to definition(
                    ServiceKind.APACHE,
                    dependencies = setOf(ServiceKind.PHP_FPM),
                ),
            ),
        )
        val manager = manager(definitions)
        try {
            manager.start(ServiceKind.APACHE)
            assertEquals(ServiceState.RUNNING, manager.snapshot(ServiceKind.PHP_FPM).state)
            assertEquals(ServiceState.RUNNING, manager.snapshot(ServiceKind.APACHE).state)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun refusesUnavailableRuntime() = runBlocking {
        val unavailable = ServiceDefinition(
            kind = ServiceKind.NGINX,
            enabled = true,
            port = 8080,
            unavailableReason = "Nginx runtime is not installed",
        )
        val manager = manager(definitions(mapOf(ServiceKind.NGINX to unavailable)))
        try {
            val exception = assertFailsWith<ServiceException> { manager.start(ServiceKind.NGINX) }
            assertTrue(exception.message?.contains("not installed") == true)
            assertEquals(ServiceState.FAILED, manager.snapshot(ServiceKind.NGINX).state)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun failsWhenConfigurationValidationFails() = runBlocking {
        val invalid = definition(ServiceKind.APACHE).copy(validationArguments = listOf("-c", "exit 9"))
        val manager = manager(definitions(mapOf(ServiceKind.APACHE to invalid)))
        try {
            assertFailsWith<ServiceException> { manager.start(ServiceKind.APACHE) }
            assertEquals(ServiceState.FAILED, manager.snapshot(ServiceKind.APACHE).state)
            assertTrue(manager.snapshot(ServiceKind.APACHE).error?.contains("validation failed") == true)
        } finally {
            manager.shutdown()
        }
    }

    private fun definition(
        kind: ServiceKind,
        dependencies: Set<ServiceKind> = emptySet(),
    ): ServiceDefinition = ServiceDefinition(
        kind = kind,
        enabled = true,
        port = 18080,
        dependencies = dependencies,
        launch = ServiceLaunch(
            executable = shell,
            arguments = listOf("-c", "sleep 30"),
            workingDirectory = root,
            environment = mapOf(
                "HOME" to root.absolutePath,
                "PATH" to "/usr/bin:/bin:/usr/local/bin",
            ),
            logFile = File(root, "${kind.id}.log"),
        ),
        healthCheck = HealthCheck.Tcp(port = 18080),
        healthTimeoutMs = 2_000,
        healthIntervalMs = 50,
    )

    private fun definitions(entries: Map<ServiceKind, ServiceDefinition>): () -> Map<ServiceKind, ServiceDefinition> =
        { entries }

    private fun manager(
        definitions: () -> Map<ServiceKind, ServiceDefinition>,
        healthProbe: HealthProbe = healthy,
    ): ServiceManager = ServiceManager(definitions, healthProbe = healthProbe)
}
