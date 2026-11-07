package dev.pocketserver.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketserver.storage.LogFileInfo
import dev.pocketserver.ui.components.ConfirmDialog
import dev.pocketserver.ui.components.EmptyState
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.theme.CodeFont

@Composable
fun LogsScreen(
    files: List<LogFileInfo>,
    selected: LogFileInfo?,
    content: String,
    onRefresh: () -> Unit,
    onSelect: (LogFileInfo) -> Unit,
    onDelete: (LogFileInfo) -> Unit,
    onCopy: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<LogFileInfo?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Logs",
                    subtitle = "Service and app logs stay inside app-private storage.",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (files.isEmpty()) {
            item {
                EmptyState(
                    title = "No log files yet",
                    body = "Logs appear after a service starts or the app records a diagnostic event.",
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { file ->
                        FilterChip(
                            selected = selected?.file?.absolutePath == file.file.absolutePath,
                            onClick = { onSelect(file) },
                            label = { Text(file.label) },
                        )
                    }
                }
            }
            if (selected != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = CodeFont,
                            )
                            Text(
                                text = "${selected.sizeBytes} bytes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onCopy(content) }) { Text("Copy") }
                        TextButton(onClick = { deleteTarget = selected }) { Text("Delete") }
                    }
                }
                item {
                    MonoBlock(text = content.ifBlank { "(empty log)" })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    deleteTarget?.let { file ->
        ConfirmDialog(
            title = "Delete log?",
            body = "Delete ${file.label}? This only removes the log file.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(file) },
            onDismiss = { deleteTarget = null },
        )
    }
}
