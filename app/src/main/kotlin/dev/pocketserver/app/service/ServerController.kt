package dev.pocketserver.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.pocketserver.core.model.ServiceKind

class ServerController(private val context: Context) {
    fun startAll() = send(ServerForegroundService.ACTION_START_ALL)

    fun stopAll() = send(ServerForegroundService.ACTION_STOP_ALL)

    fun start(kind: ServiceKind) =
        send(ServerForegroundService.ACTION_START_SERVICE, kind.id)

    fun stop(kind: ServiceKind) =
        send(ServerForegroundService.ACTION_STOP_SERVICE, kind.id)

    fun restart(kind: ServiceKind) =
        send(ServerForegroundService.ACTION_RESTART_SERVICE, kind.id)

    private fun send(action: String, serviceId: String? = null) {
        val intent = Intent(context, ServerForegroundService::class.java).setAction(action)
        if (serviceId != null) intent.putExtra(ServerForegroundService.EXTRA_SERVICE_ID, serviceId)
        ContextCompat.startForegroundService(context, intent)
    }
}
