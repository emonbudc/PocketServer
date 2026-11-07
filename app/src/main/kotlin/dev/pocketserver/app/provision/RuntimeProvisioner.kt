package dev.pocketserver.app.provision

import dev.pocketserver.runtime.RuntimeBundleDescriptor
import dev.pocketserver.runtime.RuntimeCatalogClient
import dev.pocketserver.runtime.RuntimeInstaller
import dev.pocketserver.storage.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class RuntimeProvisionState(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val current: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class RuntimeProvisioner(
    private val catalogUrl: String,
    private val installer: RuntimeInstaller,
    private val client: RuntimeCatalogClient,
    private val tempDir: File,
    private val logger: AppLogger,
    private val onRuntimesChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(RuntimeProvisionState())
    val state: StateFlow<RuntimeProvisionState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val autoStarted = AtomicBoolean(false)

    fun startAuto(scope: CoroutineScope) {
        if (!autoStarted.compareAndSet(false, true)) return
        scope.launch { provision() }
    }

    fun requestProvision(scope: CoroutineScope) {
        scope.launch { provision() }
    }

    suspend fun provision() {
        mutex.withLock {
            withContext(Dispatchers.IO) { runProvision() }
        }
    }

    private fun runProvision() {
        if (catalogUrl.isBlank()) {
            _state.value = RuntimeProvisionState(
                message = "Automatic bundle downloads are not configured.",
            )
            return
        }
        _state.value = RuntimeProvisionState(running = true, message = "Checking runtime catalog…")
        val catalog = try {
            client.fetchCatalog(catalogUrl)
        } catch (exception: Exception) {
            val message = exception.message ?: exception.javaClass.simpleName
            logger.warn(TAG, "Unable to fetch runtime catalog: $message")
            _state.value = RuntimeProvisionState(error = "Catalog download failed: $message")
            return
        }

        if (catalog.bundles.isEmpty()) {
            _state.value = RuntimeProvisionState(
                message = "The runtime catalog does not list any bundles yet.",
            )
            return
        }

        val pending = catalog.bundles.filterNot { descriptor ->
            installer.findInstalled(descriptor.id)?.manifest?.version == descriptor.version
        }
        if (pending.isEmpty()) {
            _state.value = RuntimeProvisionState(
                total = catalog.bundles.size,
                completed = catalog.bundles.size,
                message = "All ${catalog.bundles.size} catalog bundle(s) are installed.",
            )
            return
        }

        logger.info(TAG, "Provisioning ${pending.size} of ${catalog.bundles.size} runtime bundle(s)")
        _state.value = RuntimeProvisionState(
            running = true,
            total = pending.size,
            message = "Preparing ${pending.size} bundle download(s)…",
        )
        val failures = mutableListOf<String>()
        var completed = 0
        pending.forEachIndexed { index, descriptor ->
            _state.value = RuntimeProvisionState(
                running = true,
                completed = index,
                total = pending.size,
                current = descriptor.id,
                message = "Downloading ${descriptor.id} ${descriptor.version}…",
            )
            try {
                install(descriptor)
                completed++
                onRuntimesChanged()
                logger.info(
                    TAG,
                    "Installed ${descriptor.id} ${descriptor.version} from catalog",
                )
            } catch (exception: Exception) {
                val detail = exception.message ?: exception.javaClass.simpleName
                failures += "${descriptor.id}: $detail"
                logger.error(TAG, "Unable to provision ${descriptor.id}: $detail", exception)
            }
            _state.value = RuntimeProvisionState(
                running = true,
                completed = completed,
                total = pending.size,
                message = if (failures.isEmpty()) {
                    "Installed ${descriptor.id} ${descriptor.version}."
                } else {
                    "Finished ${descriptor.id} with ${failures.size} failure(s)."
                },
            )
        }

        if (failures.isEmpty()) {
            _state.value = RuntimeProvisionState(
                completed = completed,
                total = pending.size,
                message = "Runtime bundles are up to date.",
            )
        } else {
            _state.value = RuntimeProvisionState(
                completed = completed,
                total = pending.size,
                error = "Bundle provisioning failed: ${failures.joinToString("; ")}",
            )
        }
    }

    private fun install(descriptor: RuntimeBundleDescriptor) {
        val temp = File(tempDir, "catalog-${descriptor.id}-${UUID.randomUUID()}.zip")
        try {
            client.downloadBundle(descriptor, temp)
            installer.install(
                archive = temp,
                expectedSha256 = descriptor.sha256,
                overwrite = false,
                requireVerification = true,
            )
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val TAG = "runtime-catalog"
    }
}
