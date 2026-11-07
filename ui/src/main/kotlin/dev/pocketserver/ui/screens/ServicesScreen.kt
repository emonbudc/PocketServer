package dev.pocketserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceSnapshot
import dev.pocketserver.core.model.ServiceState
import dev.pocketserver.services.ServiceDefinition
import dev.pocketserver.ui.components.LabeledValue
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.components.ServiceStatusChip
import dev.pocketserver.ui.components.StatusDot
import dev.pocketserver.ui.theme.TerminalMuted

@Composable
fun ServicesScreen(
    settings: AppSettings,
    services: Map<ServiceKind, ServiceSnapshot>,
    definitions: Map<ServiceKind, ServiceDefinition>,
    onStart: (ServiceKind) -> Unit,
    onStop: (ServiceKind) -> Unit,
    onRestart: (ServiceKind) -> Unit,
    onOpenLogs: (ServiceKind) -> Unit,
    onOpenRuntime: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader(
                title = "Service manager",
                subtitle = "Dependencies start first. A service is RUNNING only after its health probe succeeds.",
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Active web server: ${settings.webServer.displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Port ${settings.httpPort} · loopback ${if (settings.lanAccessEnabled) "off (LAN enabled)" else "only"}. " +
                            "Changing the web server or ports requires a service restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenRuntime) { Text("Runtime Manager") }
                }
            }
        }

        items(ServiceKind.entries.sortedBy { it.startupOrder }, key = { it.id }) { kind ->
            val snapshot = services[kind] ?: ServiceSnapshot(kind)
            val definition = definitions[kind]
            ServiceDetailCard(
                snapshot = snapshot,
                definition = definition,
                onStart = { onStart(kind) },
                onStop = { onStop(kind) },
                onRestart = { onRestart(kind) },
                onOpenLogs = { onOpenLogs(kind) },
            )
        }

        item {
            Text(
                text = "Phase 1 note: service definitions require an imported runtime bundle and matching configuration files. " +
                    "If a runtime or config is missing, the service remains unavailable instead of pretending to run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun ServiceDetailCard(
    snapshot: ServiceSnapshot,
    definition: ServiceDefinition?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val available = definition?.isAvailable == true
    val reason = definition?.initialUnavailableReason()
    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(snapshot.state, size = 11)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.kind.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshot.kind.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalMuted,
                    )
                }
                ServiceStatusChip(snapshot)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LabeledValue("Port", snapshot.port.toString(), mono = true)
                LabeledValue("PID", snapshot.pid?.toString() ?: "—", mono = true)
                LabeledValue("Health", snapshot.healthDetail ?: "—")
            }

            if (snapshot.error != null) {
                MonoBlock(text = snapshot.error, error = true)
            } else if (reason != null) {
                MonoBlock(text = reason)
            }

            if (snapshot.state == ServiceState.RUNNING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStop) { Text("Stop") }
                    OutlinedButton(onClick = onRestart) { Text("Restart") }
                    OutlinedButton(onClick = onOpenLogs) { Text("Logs") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = onStart,
                        enabled = available && !snapshot.isBusy,
                    ) { Text(if (snapshot.isBusy) "Working…" else "Start") }
                    OutlinedButton(onClick = onOpenLogs) { Text("Logs") }
                }
            }

            if (definition != null && definition.enabled && !available) {
                Text(
                    text = "Unavailable: ${reason ?: "runtime missing"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
