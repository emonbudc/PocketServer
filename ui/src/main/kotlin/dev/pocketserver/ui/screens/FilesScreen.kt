package dev.pocketserver.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import dev.pocketserver.core.util.Formatting
import dev.pocketserver.storage.ProjectEntry
import dev.pocketserver.ui.FilePreview
import dev.pocketserver.ui.components.ConfirmDialog
import dev.pocketserver.ui.components.EmptyState
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.theme.CodeFont

@Composable
fun FilesScreen(
    project: String?,
    currentPath: String,
    entries: List<ProjectEntry>,
    preview: FilePreview?,
    busy: Boolean,
    onChooseProject: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenEntry: (ProjectEntry) -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onExport: (Uri) -> Unit,
    onSavePreview: (String) -> Unit,
    onClosePreview: () -> Unit,
) {
    var showCreateFolder by remember { mutableStateOf(false) }
    var showCreateFile by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectEntry?>(null) }
    var exportName by remember { mutableStateOf("file.txt") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) onExport(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentPath.isNotBlank()) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Up one directory")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(
                        title = project ?: "File manager",
                        subtitle = if (project == null) {
                            "Select a project to browse its files."
                        } else {
                            "htdocs/${project}${if (currentPath.isBlank()) "" else "/$currentPath"}"
                        },
                    )
                }
                TextButton(onClick = onChooseProject) { Text("Projects") }
            }
        }

        if (project == null) {
            item {
                EmptyState(
                    title = "No project selected",
                    body = "Open Projects and choose a project directory first.",
                    actionLabel = "Open Projects",
                    onAction = onChooseProject,
                )
            }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        folderName = ""
                        showCreateFolder = true
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Folder")
                }
                OutlinedButton(
                    onClick = {
                        fileName = ""
                        fileContent = ""
                        showCreateFile = true
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("File")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy,
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Import")
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "Empty directory",
                    body = "Create a file or import one from device storage.",
                )
            }
        }

        items(entries, key = { it.path }) { entry ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenEntry(entry) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = if (entry.isDirectory) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (entry.isDirectory) "directory" else {
                                "${Formatting.bytes(entry.sizeBytes)} · ${Formatting.timestamp(entry.modifiedAtEpochMs)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = CodeFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { deleteTarget = entry }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                    }
                }
            }
        }

        if (preview != null) {
            item {
                var editContent by remember(preview.relativePath) {
                    mutableStateOf(preview.content.take(200_000))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionHeader(
                    title = "Preview and edit",
                    subtitle = preview.relativePath,
                )
                Spacer(Modifier.height(8.dp))
                if (preview.isBinary) {
                    MonoBlock("Binary file. Export it to inspect it on a desktop.")
                } else {
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeFont),
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSavePreview(editContent) },
                        enabled = !preview.isBinary,
                    ) { Text("Save") }
                    OutlinedButton(onClick = {
                        exportName = preview.relativePath.substringAfterLast('/')
                        exportLauncher.launch(exportName)
                    }) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Export")
                    }
                    OutlinedButton(onClick = onClosePreview) { Text("Close") }
                }
            }
        }
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateFolder(folderName.trim())
                        showCreateFolder = false
                    },
                    enabled = folderName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("Cancel") } },
        )
    }

    if (showCreateFile) {
        AlertDialog(
            onDismissRequest = { showCreateFile = false },
            title = { Text("New text file") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("Initial content") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateFile(fileName.trim(), fileContent)
                        showCreateFile = false
                    },
                    enabled = fileName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFile = false }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { entry ->
        ConfirmDialog(
            title = "Delete ${entry.name}?",
            body = if (entry.isDirectory) {
                "This permanently deletes the directory and everything inside it."
            } else {
                "This permanently deletes the file."
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDeleteEntry(entry.path) },
            onDismiss = { deleteTarget = null },
        )
    }
}
