package dev.pocketserver.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.model.InstalledRuntime
import dev.pocketserver.core.util.Formatting
import dev.pocketserver.core.util.Sha256
import dev.pocketserver.ui.components.ConfirmDialog
import dev.pocketserver.ui.components.EmptyState
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.components.StatusDot
import dev.pocketserver.ui.theme.CodeFont
import dev.pocketserver.ui.theme.TerminalGreen
import dev.pocketserver.core.model.ServiceState

@Composable
fun RuntimeScreen(
    runtimes: List<InstalledRuntime>,
    deviceAbis: List<String>,
    probeOutput: String?,
    probeRunning: Boolean,
    probeError: String?,
    busy: Boolean,
    message: String?,
    autoRunning: Boolean,
    autoMessage: String?,
    autoError: Boolean,
    autoProgress: Float?,
    onCheckBundles: () -> Unit,
    onImport: (Uri, String?, Boolean) -> Unit,
    onRunProbe: () -> Unit,
    onUninstall: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var expectedSha by remember { mutableStateOf("") }
    var requireChecksum by remember { mutableStateOf(true) }
    var uninstallTarget by remember { mutableStateOf<String?>(null) }
    var shaError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            expectedSha = ""
            shaError = null
            pendingUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Runtime manager",
                    subtitle = "Install arm64-v8a runtime bundles into app-private storage.",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Device compatibility",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Supported ABI: arm64-v8a · Device ABIs: ${deviceAbis.joinToString(", ").ifBlank { "unknown" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = CodeFont,
                    )
                    if ("arm64-v8a" !in deviceAbis) {
                        MonoBlock(
                            "This device does not report arm64-v8a. PocketServer is built for ARM64 only.",
                            error = true,
                        )
                    }
                    Text(
                        text = "Bundles must contain manifest.json and AArch64 ELF entrypoints. " +
                            "Archives are rejected for traversal paths, symlinks and non-ARM64 executables.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(
                            title = "Automatic runtime bundles",
                            subtitle = "Downloaded and SHA-256 verified after installation.",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onCheckBundles,
                            enabled = !autoRunning && !busy,
                        ) {
                            Text(if (autoRunning) "Downloading…" else "Check now")
                        }
                    }
                    if (autoRunning) {
                        if (autoProgress != null) {
                            LinearProgressIndicator(
                                progress = { autoProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (autoMessage != null) {
                        Text(
                            text = autoMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = "The catalog is fetched over HTTPS. Every archive must match its " +
                            "published SHA-256 before extraction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Installing…" else "Import bundle")
                }
                OutlinedButton(onClick = onRunProbe, enabled = !probeRunning && "arm64-v8a" in deviceAbis) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (probeRunning) "Running…" else "Native probe")
                }
            }
        }
        if (message != null) {
            item { MonoBlock(message, error = message.contains("failed", ignoreCase = true)) }
        }
        if (probeError != null) {
            item { MonoBlock(probeError, error = true) }
        }
        if (probeOutput != null) {
            item {
                SectionHeader(
                    title = "Native execution proof",
                    subtitle = "Executed from app-private storage without root or Termux.",
                )
                Spacer(Modifier.height(8.dp))
                MonoBlock(probeOutput)
            }
        }
        item {
            SectionHeader(
                title = "Installed runtimes",
                subtitle = if (runtimes.isEmpty()) "No packages installed." else "${runtimes.size} package(s).",
            )
        }
        if (runtimes.isEmpty()) {
            item {
                EmptyState(
                    title = "Runtime storage is empty",
                    body = "Import a compatible bundle. PocketServer does not download binaries from unverified URLs.",
                )
            }
        }
        items(runtimes, key = { it.manifest.id }) { runtime ->
            RuntimeCard(runtime = runtime, onUninstall = { uninstallTarget = runtime.manifest.id })
        }
        item {
            SectionHeader(title = "Bundle format", subtitle = "Phase 1 contract")
            Spacer(Modifier.height(8.dp))
            MonoBlock(
                """
                manifest.json
                {
                  "formatVersion": 1,
                  "id": "php",
                  "version": "8.4.1",
                  "abi": "arm64-v8a",
                  "entrypoints": {
                    "php": "bin/php",
                    "php-fpm": "sbin/php-fpm"
                  }
                }
                bin/...   sbin/...   lib/...
                """.trimIndent(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Expected IDs: php, apache, nginx, mariadb, mysql, node, python, git. " +
                    "An entrypoint must be an AArch64 PIE/exec ELF and is chmod 0755 after extraction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Verify and install") },
            text = {
                Column {
                    Text(
                        text = "SHA-256 is the trust anchor. Compute it from the bundle you built or downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = expectedSha,
                        onValueChange = {
                            expectedSha = it
                            shaError = null
                        },
                        label = { Text("Expected SHA-256") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeFont),
                        isError = shaError != null || (requireChecksum && expectedSha.isNotBlank() && !Sha256.isHexDigest(expectedSha)),
                        supportingText = {
                            Text(shaError ?: if (requireChecksum) "Required while verification is enforced" else "Optional")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require checksum", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Reject the bundle if the digest is missing or wrong.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = requireChecksum,
                            onCheckedChange = { requireChecksum = it },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sha = expectedSha.trim().ifBlank { null }
                        if (sha == null && requireChecksum) {
                            shaError = "A SHA-256 digest is required"
                        } else if (sha != null && !Sha256.isHexDigest(sha)) {
                            shaError = "Digest must be 64 hexadecimal characters"
                        } else {
                            onImport(uri, sha, requireChecksum)
                            pendingUri = null
                        }
                    },
                ) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Cancel") } },
        )
    }

    uninstallTarget?.let { id ->
        ConfirmDialog(
            title = "Remove $id?",
            body = "The runtime package and its files will be deleted. Stop services that use it first.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { onUninstall(id) },
            onDismiss = { uninstallTarget = null },
        )
    }
}

@Composable
private fun RuntimeCard(runtime: InstalledRuntime, onUninstall: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(ServiceState.RUNNING, size = 9)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${runtime.manifest.id} ${runtime.manifest.version}",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = CodeFont,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = runtime.manifest.abi + " · " + if (runtime.verified) "checksum verified" else "unverified import",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (runtime.verified) TerminalGreen else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = runtime.manifest.entrypoints.entries.joinToString(" · ") { "${it.key} → ${it.value}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Installed ${Formatting.timestamp(runtime.installedAtEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Uninstall runtime")
            }
        }
    }
}
