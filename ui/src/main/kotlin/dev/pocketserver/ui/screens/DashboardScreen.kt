package dev.pocketserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.model.InstalledRuntime
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.core.model.ServiceSnapshot
import dev.pocketserver.core.model.ServiceState
import dev.pocketserver.core.util.Formatting
import dev.pocketserver.network.NetworkBinding
import dev.pocketserver.services.ServiceDefinition
import dev.pocketserver.ui.StorageUsage
import dev.pocketserver.ui.components.LabeledValue
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.components.ServiceStatusChip
import dev.pocketserver.ui.components.StatusDot
import dev.pocketserver.ui.components.stateColor
import dev.pocketserver.ui.theme.CodeFont
import dev.pocketserver.ui.theme.TerminalGreen
import java.util.Locale

@Composable
fun DashboardScreen(
    settings: AppSettings,
    services: Map<ServiceKind, ServiceSnapshot>,
    definitions: Map<ServiceKind, ServiceDefinition>,
    runtimes: List<InstalledRuntime>,
    storage: StorageUsage,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onStartService: (ServiceKind) -> Unit,
    onStopService: (ServiceKind) -> Unit,
    onOpenServices: () -> Unit,
    onOpenRuntime: () -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val ordered = ServiceKind.entries.sortedBy { it.startupOrder }
    val running = services.values.count { it.state == ServiceState.RUNNING }
    val available = definitions.values.count { it.isAvailable }
    val address = NetworkBinding.localUrl(settings.httpPort)
    val usedBytes = (storage.totalBytes - storage.freeBytes).coerceAtLeast(0)
    val usedFraction = if (storage.totalBytes > 0) {
        (usedBytes.toFloat() / storage.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        text = "LOCAL DEVELOPMENT ENVIRONMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = CodeFont,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Your stack. Your phone.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No root. No Termux. No external app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HTTP ENDPOINT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = CodeFont,
                                    fontWeight = FontWeight.Medium,
                                    color = TerminalGreen,
                                )
                            }
                            IconButton(onClick = { onCopyAddress(address) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy address")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStartAll,
                            enabled = available > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start all")
                        }
                        OutlinedButton(
                            onClick = onStopAll,
                            enabled = running > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop all")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Running",
                    value = "$running / ${ServiceKind.entries.size}",
                    detail = if (running > 0) "services healthy" else "nothing running",
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Runtimes",
                    value = runtimes.size.toString(),
                    detail = if (runtimes.isEmpty()) "none installed" else "packages installed",
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Services",
                    subtitle = "Health-checked before a service is reported running.",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onOpenServices) { Text("Manage") }
            }
        }

        items(ordered, key = { it.id }) { kind ->
            val snapshot = services[kind] ?: ServiceSnapshot(kind)
            val definition = definitions[kind]
            ServiceSummaryRow(
                snapshot = snapshot,
                selectedWebServer = settings.webServer,
                unavailableReason = definition?.initialUnavailableReason(),
                onStart = { onStartService(kind) },
                onStop = { onStopService(kind) },
            )
        }

        item {
            SectionHeader(
                title = "Web server",
                subtitle = "Only one HTTP server can own port ${settings.httpPort}.",
            )
        }
        item {
            PocketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        services[settings.webServer]?.state ?: ServiceState.UNKNOWN,
                        size = 12,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.webServer.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (settings.lanAccessEnabled) {
                                "LAN access enabled — binding to ${NetworkBinding.ALL_INTERFACES}"
                            } else {
                                "Loopback only — ${NetworkBinding.LOOPBACK}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ServiceStatusChip(services[settings.webServer] ?: ServiceSnapshot(settings.webServer))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Runtime",
                    subtitle = "ARM64 packages installed in the app-private runtime.",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onOpenRuntime) { Text("Open") }
            }
        }
        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (runtimes.isEmpty()) {
                        MonoBlock("No runtimes installed.\nImport a checksum-verified arm64-v8a bundle from Runtime Manager.")
                    } else {
                        runtimes.forEach { runtime ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(ServiceState.RUNNING, size = 8)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = runtime.manifest.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = CodeFont,
                                    modifier = Modifier.width(82.dp),
                                )
                                Text(
                                    text = runtime.manifest.version,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (runtime.verified) "verified" else "unverified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (runtime.verified) TerminalGreen else MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LabeledValue("Free storage", Formatting.bytes(storage.freeBytes))
                        LabeledValue("Runtime files", Formatting.bytes(storage.runtimeBytes))
                    }
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        color = if (usedFraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${Formatting.bytes(usedBytes)} used of ${Formatting.bytes(storage.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Local only by default. " +
                    "LAN access is opt-in and database ports stay loopback unless you change the runtime configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = CodeFont,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServiceSummaryRow(
    snapshot: ServiceSnapshot,
    selectedWebServer: ServiceKind,
    unavailableReason: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val definitionLabel = when (snapshot.kind) {
        ServiceKind.APACHE, ServiceKind.NGINX -> if (snapshot.kind == selectedWebServer) {
            "port ${snapshot.port} · selected"
        } else {
            "port ${snapshot.port} · not selected"
        }
        else -> "port ${snapshot.port}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(snapshot.state, size = 10)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.kind.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = unavailableReason ?: definitionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor(snapshot.state).takeIf { snapshot.state == ServiceState.FAILED }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (snapshot.state == ServiceState.RUNNING) {
                OutlinedButton(onClick = onStop) { Text("Stop") }
            } else {
                OutlinedButton(
                    onClick = onStart,
                    enabled = unavailableReason == null && !snapshot.isBusy,
                ) { Text(if (snapshot.isBusy) "…" else "Start") }
            }
            Spacer(Modifier.width(8.dp))
            ServiceStatusChip(snapshot)
        }
    }
}
