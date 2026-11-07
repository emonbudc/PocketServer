package dev.pocketserver.app.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketserver.app.di.AppContainer
import dev.pocketserver.storage.ProjectEntry
import dev.pocketserver.storage.ProjectStore
import dev.pocketserver.ui.FilePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class WorkspaceViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.projects

    private val _projects = MutableStateFlow<List<ProjectEntry>>(emptyList())
    val projects: StateFlow<List<ProjectEntry>> = _projects.asStateFlow()

    private val _selectedProject = MutableStateFlow<String?>(null)
    val selectedProject: StateFlow<String?> = _selectedProject.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _entries = MutableStateFlow<List<ProjectEntry>>(emptyList())
    val entries: StateFlow<List<ProjectEntry>> = _entries.asStateFlow()

    private val _preview = MutableStateFlow<FilePreview?>(null)
    val preview: StateFlow<FilePreview?> = _preview.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.listProjects() }
                .onSuccess { _projects.value = it }
                .onFailure { fail("Unable to list projects", it) }
        }
    }

    fun openProject(name: String) {
        _selectedProject.value = name
        _currentPath.value = ""
        _preview.value = null
        loadEntries()
    }

    fun chooseProject(name: String?) {
        _selectedProject.value = name
        _currentPath.value = ""
        _preview.value = null
        loadEntries()
    }

    fun loadEntries() {
        val project = _selectedProject.value
        if (project == null) {
            _entries.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.listEntries(project, _currentPath.value) }
                .onSuccess { _entries.value = it }
                .onFailure { fail("Unable to list files", it) }
        }
    }

    fun openEntry(entry: ProjectEntry) {
        if (entry.isDirectory) {
            _currentPath.value = entry.path
            _preview.value = null
            loadEntries()
        } else {
            loadPreview(entry.path)
        }
    }

    fun navigateUp() {
        if (_currentPath.value.isBlank()) return
        _currentPath.value = _currentPath.value.substringBeforeLast('/', "")
        _preview.value = null
        loadEntries()
    }

    fun loadPreview(relativePath: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = store.readBytes(project, relativePath, PREVIEW_LIMIT_BYTES)
                val binary = bytes.take(4096).any { it == 0.toByte() }
                FilePreview(
                    relativePath = relativePath,
                    content = if (binary) "" else String(bytes, Charsets.UTF_8),
                    isBinary = binary,
                )
            }.onSuccess { _preview.value = it }
                .onFailure { fail("Unable to open file", it) }
        }
    }

    fun savePreview(content: String) {
        val project = _selectedProject.value ?: return
        val preview = _preview.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching {
                store.writeText(project, preview.relativePath, content)
                store.listEntries(project, _currentPath.value)
            }.onSuccess {
                _entries.value = it
                _preview.value = preview.copy(content = content)
                _message.value = "Saved ${preview.relativePath}"
            }.onFailure { fail("Unable to save file", it) }
            _busy.value = false
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching { store.createProject(name) }
                .onSuccess {
                    _message.value = "Created project $name"
                    _projects.value = store.listProjects()
                }
                .onFailure { fail("Unable to create project", it) }
            _busy.value = false
        }
    }

    fun deleteProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching { store.deleteProject(name) }
                .onSuccess {
                    if (_selectedProject.value == name) {
                        _selectedProject.value = null
                        _currentPath.value = ""
                        _entries.value = emptyList()
                        _preview.value = null
                    }
                    _message.value = "Deleted project $name"
                    _projects.value = store.listProjects()
                }
                .onFailure { fail("Unable to delete project", it) }
            _busy.value = false
        }
    }

    fun createFolder(name: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.createDirectory(project, _currentPath.value, name) }
                .onSuccess {
                    _message.value = "Created folder $name"
                    _entries.value = store.listEntries(project, _currentPath.value)
                }
                .onFailure { fail("Unable to create folder", it) }
        }
    }

    fun createFile(name: String, content: String) {
        val project = _selectedProject.value ?: return
        val relative = joinPath(_currentPath.value, name)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.writeText(project, relative, content) }
                .onSuccess {
                    _message.value = "Created ${name}"
                    _entries.value = store.listEntries(project, _currentPath.value)
                }
                .onFailure { fail("Unable to create file", it) }
        }
    }

    fun deleteEntry(relativePath: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.deleteEntry(project, relativePath) }
                .onSuccess {
                    val previewPath = _preview.value?.relativePath
                    if (previewPath != null &&
                        (previewPath == relativePath || previewPath.startsWith("$relativePath/"))
                    ) {
                        _preview.value = null
                    }
                    _message.value = "Deleted $relativePath"
                    _entries.value = store.listEntries(project, _currentPath.value)
                }
                .onFailure { fail("Unable to delete", it) }
        }
    }

    fun importDocument(uri: Uri) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching {
                val displayName = queryDisplayName(uri) ?: "imported-${System.currentTimeMillis()}"
                val safeName = sanitizeName(displayName)
                val bytes = readLimited(uri, ProjectStore.MAX_WRITE_BYTES)
                val target = joinPath(_currentPath.value, safeName)
                store.writeBytes(project, target, bytes)
                target
            }.onSuccess {
                _message.value = "Imported $it"
                _entries.value = store.listEntries(project, _currentPath.value)
            }.onFailure { fail("Import failed", it) }
            _busy.value = false
        }
    }

    fun exportPreview(uri: Uri) {
        val project = _selectedProject.value ?: return
        val preview = _preview.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching {
                val bytes = store.readBytes(project, preview.relativePath, ProjectStore.MAX_WRITE_BYTES)
                val output = container.context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Unable to open the selected destination")
                output.use { it.write(bytes) }
            }.onSuccess {
                _message.value = "Exported ${preview.relativePath}"
            }.onFailure { fail("Export failed", it) }
            _busy.value = false
        }
    }

    fun closePreview() {
        _preview.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            container.context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun readLimited(uri: Uri, limit: Long): ByteArray {
        val input = container.context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected file")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "File exceeds the ${limit / (1024 * 1024)} MiB limit" }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
        return cleaned.ifBlank { "imported-${System.currentTimeMillis()}" }.take(120)
    }

    private fun joinPath(parent: String, name: String): String =
        if (parent.isBlank()) name else "$parent/$name"

    private fun fail(prefix: String, throwable: Throwable) {
        _message.value = "$prefix: ${throwable.message ?: throwable.javaClass.simpleName}"
    }

    companion object {
        private const val PREVIEW_LIMIT_BYTES = 512L * 1024L
    }
}
