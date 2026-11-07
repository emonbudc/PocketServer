package dev.pocketserver.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.pocketserver.app.MainActivity
import dev.pocketserver.app.PocketServerApplication
import dev.pocketserver.app.R
import dev.pocketserver.app.di.AppContainer
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceSnapshot
import dev.pocketserver.core.model.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as PocketServerApplication).container
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Preparing services…"))

        when (intent?.action) {
            ACTION_START_ALL -> startAll()
            ACTION_STOP_ALL -> stopAll()
            ACTION_START_SERVICE -> withService(intent) { startServiceKind(it) }
            ACTION_STOP_SERVICE -> withService(intent) { stopServiceKind(it) }
            ACTION_RESTART_SERVICE -> withService(intent) { restartServiceKind(it) }
            null -> {
                if (container.config.desiredRunning() &&
                    container.config.snapshot().autoRestartAfterProcessDeath
                ) {
                    container.logger.info("foreground", "Restoring services after process recreation")
                    startAll()
                } else {
                    stopSelfCleanly()
                }
            }
            else -> stopSelfCleanly()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAll() {
        container.config.setDesiredRunning(true)
        serviceScope.launch {
            updateNotification("Starting services…")
            try {
                container.serviceManager.startAll()
            } catch (exception: Exception) {
                container.logger.error("foreground", "startAll failed", exception)
            }
            updateNotificationFromState()
            if (hasLiveServices()) {
                monitorServices()
            } else {
                container.config.setDesiredRunning(false)
                stopSelfCleanly()
            }
        }
    }

    private fun stopAll() {
        container.config.setDesiredRunning(false)
        monitorJob?.cancel()
        serviceScope.launch {
            updateNotification("Stopping services…")
            try {
                container.serviceManager.stopAll()
            } catch (exception: Exception) {
                container.logger.error("foreground", "stopAll failed", exception)
            }
            container.logger.info("foreground", "All services stopped")
            stopSelfCleanly()
        }
    }

    private fun startServiceKind(kind: ServiceKind) {
        container.config.setDesiredRunning(true)
        serviceScope.launch {
            try {
                container.serviceManager.start(kind)
            } catch (exception: Exception) {
                container.logger.error("foreground", "Failed to start ${kind.id}", exception)
            }
            updateNotificationFromState()
            if (hasLiveServices()) monitorServices() else {
                container.config.setDesiredRunning(false)
                stopSelfCleanly()
            }
        }
    }

    private fun stopServiceKind(kind: ServiceKind) {
        serviceScope.launch {
            try {
                container.serviceManager.stop(kind)
            } catch (exception: Exception) {
                container.logger.error("foreground", "Failed to stop ${kind.id}", exception)
            }
            updateNotificationFromState()
            if (!hasLiveServices()) {
                container.config.setDesiredRunning(false)
                monitorJob?.cancel()
                stopSelfCleanly()
            }
        }
    }

    private fun restartServiceKind(kind: ServiceKind) {
        container.config.setDesiredRunning(true)
        serviceScope.launch {
            try {
                container.serviceManager.stop(kind)
                container.serviceManager.start(kind)
            } catch (exception: Exception) {
                container.logger.error("foreground", "Failed to restart ${kind.id}", exception)
            }
            updateNotificationFromState()
            if (hasLiveServices()) monitorServices() else {
                container.config.setDesiredRunning(false)
                stopSelfCleanly()
            }
        }
    }

    private fun withService(intent: Intent, block: (ServiceKind) -> Unit) {
        val kind = intent.getStringExtra(EXTRA_SERVICE_ID)?.let(ServiceKind::fromId)
        if (kind == null) {
            container.logger.warn("foreground", "Missing or invalid service id")
            stopSelfCleanly()
            return
        }
        block(kind)
    }

    private fun monitorServices() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            container.serviceManager.snapshots.collect { snapshots ->
                updateNotificationFromState(snapshots)
                if (snapshots.values.none { it.state.isActive }) {
                    delay(1_000)
                    if (container.serviceManager.snapshots.value.values.none { it.state.isActive }) {
                        container.config.setDesiredRunning(false)
                        monitorJob?.cancel()
                        stopSelfCleanly()
                    }
                }
            }
        }
    }

    private fun hasLiveServices(): Boolean =
        container.serviceManager.snapshots.value.values.any { it.state.isActive }

    private fun updateNotificationFromState(snapshots: Map<ServiceKind, ServiceSnapshot> = container.serviceManager.snapshots.value) {
        val running = snapshots.values.count { it.state == ServiceState.RUNNING }
        val starting = snapshots.values.count { it.state == ServiceState.STARTING }
        val settings = container.config.snapshot()
        val text = when {
            running > 0 -> "$running running · ${settings.webServer.displayName} :${settings.httpPort}"
            starting > 0 -> "Starting $starting service(s)…"
            else -> "No services running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun stopSelfCleanly() {
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerForegroundService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop all", stopIntent)
            .build()
    }

    private val ServiceState.isActive: Boolean
        get() = this == ServiceState.STARTING || this == ServiceState.RUNNING || this == ServiceState.STOPPING

    companion object {
        const val ACTION_START_ALL = "dev.pocketserver.action.START_ALL"
        const val ACTION_STOP_ALL = "dev.pocketserver.action.STOP_ALL"
        const val ACTION_START_SERVICE = "dev.pocketserver.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "dev.pocketserver.action.STOP_SERVICE"
        const val ACTION_RESTART_SERVICE = "dev.pocketserver.action.RESTART_SERVICE"
        const val EXTRA_SERVICE_ID = "service_id"
        private const val CHANNEL_ID = "pocket_server_runtime"
        private const val NOTIFICATION_ID = 4101
    }
}
