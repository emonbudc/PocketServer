package dev.pocketserver.ui.screens

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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import dev.pocketserver.ui.components.ConfirmDialog
import dev.pocketserver.ui.components.EmptyState
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.theme.CodeFont

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntry>,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    projectName = ""
                    showCreate = true
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New project") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = "Projects",
                        subtitle = "Each project is a directory inside app-private htdocs.",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }
            if (projects.isEmpty()) {
                item {
                    EmptyState(
                        title = "No projects yet",
                        body = "Create a project to get a starter index.php in htdocs.",
                        actionLabel = "Create project",
                        onAction = {
                            projectName = ""
                            showCreate = true
                        },
                    )
                }
            }
            items(projects, key = { it.path }) { project ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(project.name) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = project.path,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = CodeFont,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Modified ${Formatting.timestamp(project.modifiedAtEpochMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deleteTarget = project.name }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete project")
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New project") },
            text = {
                Column {
                    Text(
                        "A folder and starter index.php will be created in htdocs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project name") },
                        singleLine = true,
                        supportingText = { Text("Letters, digits, dot, dash, underscore") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreate(projectName.trim())
                        showCreate = false
                    },
                    enabled = projectName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { name ->
        ConfirmDialog(
            title = "Delete $name?",
            body = "This permanently removes the project directory and everything inside it. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(name) },
            onDismiss = { deleteTarget = null },
        )
    }
}
