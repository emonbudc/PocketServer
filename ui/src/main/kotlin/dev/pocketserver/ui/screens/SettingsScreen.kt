package dev.pocketserver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.core.config.ThemeMode
import dev.pocketserver.core.model.ServiceKind
import dev.pocketserver.ui.components.ConfirmDialog
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.theme.CodeFont

@Composable
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    onSetTheme: (ThemeMode) -> Unit,
    onSetWebServer: (ServiceKind) -> Unit,
    onSetLan: (Boolean) -> Unit,
    onSetAutoRestart: (Boolean) -> Unit,
    onOpenPorts: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    var confirmLan by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader(
                title = "Settings",
                subtitle = "Local-first defaults. Changes are applied to service definitions immediately.",
            )
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSetTheme(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Web server", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Apache and Nginx are mutually exclusive on the HTTP port.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ServiceKind.APACHE, ServiceKind.NGINX).forEach { server ->
                            FilterChip(
                                selected = settings.webServer == server,
                                onClick = { onSetWebServer(server) },
                                label = { Text(server.displayName) },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        title = "LAN access",
                        subtitle = "Expose services on 0.0.0.0 instead of loopback. Off by default. " +
                            "Only use this on a trusted network.",
                        checked = settings.lanAccessEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) confirmLan = true else onSetLan(false)
                        },
                    )
                    SettingSwitchRow(
                        title = "Restart after process death",
                        subtitle = "If Android kills the process while services were running, " +
                            "the foreground service attempts to restore the stack.",
                        checked = settings.autoRestartAfterProcessDeath,
                        onCheckedChange = onSetAutoRestart,
                    )
                }
            }
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Network and storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenPorts) { Text("Port manager") }
                        OutlinedButton(onClick = onOpenAppSettings) { Text("App system settings") }
                    }
                    Text(
                        text = "Port bindings: HTTP ${settings.httpPort}, PHP-FPM ${settings.phpFpmPort}, " +
                            "MariaDB ${settings.mariadbPort}, MySQL ${settings.mysqlPort}. " +
                            "Databases remain loopback-only unless their imported configuration says otherwise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("About this build", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "PocketServer $versionName · Phase 1 foundation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = CodeFont,
                    )
                    MonoBlock(
                        "This build does not fake service readiness. If a runtime or configuration is missing, " +
                            "the service stays unavailable until you install it.",
                    )
                    OutlinedButton(onClick = onOpenAbout) { Text("Read about PocketServer") }
                }
            }
        }
    }

    if (confirmLan) {
        ConfirmDialog(
            title = "Enable LAN access?",
            body = "This can expose your local development services to other devices on the current network. " +
                "Keep it disabled on public Wi-Fi and do not expose database ports. You must also restart services " +
                "after updating the runtime configuration.",
            confirmLabel = "Enable LAN",
            destructive = true,
            onConfirm = { onSetLan(true) },
            onDismiss = { confirmLan = false },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
