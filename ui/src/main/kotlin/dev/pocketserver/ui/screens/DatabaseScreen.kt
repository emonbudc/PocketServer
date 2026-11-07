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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.database.DatabaseEngine
import dev.pocketserver.database.DatabaseReadiness
import dev.pocketserver.ui.components.LabeledValue
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.components.StatusDot
import dev.pocketserver.ui.theme.TerminalGreen
import dev.pocketserver.ui.theme.TerminalMuted
import dev.pocketserver.core.model.ServiceState

@Composable
fun DatabaseScreen(
    settings: AppSettings,
    readiness: List<DatabaseReadiness>,
    busyEngine: DatabaseEngine?,
    onPrepare: (DatabaseEngine) -> Unit,
    onRefresh: () -> Unit,
    onCopyPassword: (DatabaseEngine) -> Unit,
    onOpenPorts: () -> Unit,
    onOpenRuntime: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Database engines",
                    subtitle = "Separate binaries, data directories and ports. Nothing is reachable outside loopback by default.",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        item {
            MonoBlock(
                "Random database credentials are generated on first request and stored with AES-GCM " +
                    "in the Android Keystore. Plaintext passwords are never written to logs or config.",
            )
        }
        items(readiness, key = { it.engine.id }) { item ->
            DatabaseCard(
                readiness = item,
                configuredPort = settings.portFor(
                    if (item.engine == DatabaseEngine.MARIADB) {
                        dev.pocketserver.core.model.ServiceKind.MARIADB
                    } else {
                        dev.pocketserver.core.model.ServiceKind.MYSQL
                    },
                ),
                busy = busyEngine == item.engine,
                onPrepare = { onPrepare(item.engine) },
                onCopyPassword = { onCopyPassword(item.engine) },
            )
        }
        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Phase 1 boundary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "This build validates runtime, executable, configuration and data-directory readiness. " +
                            "Automatic server bootstrap (mysql_install_db / mariadb-install-db), SQL import/export and " +
                            "phpMyAdmin integration are Phase 3–4 work and are not simulated here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenRuntime) { Text("Runtime Manager") }
                        OutlinedButton(onClick = onOpenPorts) { Text("Port Manager") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseCard(
    readiness: DatabaseReadiness,
    configuredPort: Int,
    busy: Boolean,
    onPrepare: () -> Unit,
    onCopyPassword: () -> Unit,
) {
    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    if (readiness.ready) ServiceState.RUNNING else ServiceState.FAILED,
                    size = 11,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = readiness.engine.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (readiness.ready) "Ready to launch" else "Not ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (readiness.ready) TerminalGreen else TerminalMuted,
                    )
                }
                Text(
                    text = "port $configuredPort",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LabeledValue("Runtime", readiness.runtimeVersion ?: "not installed", mono = true)
                LabeledValue("Executable", if (readiness.serverExecutablePresent) "present" else "missing")
                LabeledValue("Config", if (readiness.configurationPresent) "present" else "missing")
            }
            MonoBlock(
                text = buildString {
                    appendLine("data: ${readiness.dataDirectoryPath}")
                    appendLine("config: ${readiness.configurationPath}")
                    append(readiness.executablePath ?: "executable: —")
                },
            )
            if (!readiness.ready) {
                MonoBlock(
                    text = readiness.issues.joinToString("\n") { "• $it" },
                    error = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrepare, enabled = !busy) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Preparing…" else "Prepare data directory")
                }
                Button(onClick = onCopyPassword) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy password")
                }
            }
        }
    }
}
