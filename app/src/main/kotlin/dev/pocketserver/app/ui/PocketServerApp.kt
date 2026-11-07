package dev.pocketserver.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketserver.app.BuildConfig
import dev.pocketserver.app.viewmodel.AppViewModelFactory
import dev.pocketserver.app.viewmodel.ServerViewModel
import dev.pocketserver.app.viewmodel.ToolingViewModel
import dev.pocketserver.app.viewmodel.WorkspaceViewModel
import dev.pocketserver.ui.screens.AboutScreen
import dev.pocketserver.ui.screens.DashboardScreen
import dev.pocketserver.ui.screens.DatabaseScreen
import dev.pocketserver.ui.screens.FilesScreen
import dev.pocketserver.ui.screens.LogsScreen
import dev.pocketserver.ui.screens.PortsScreen
import dev.pocketserver.ui.screens.ProjectsScreen
import dev.pocketserver.ui.screens.RuntimeScreen
import dev.pocketserver.ui.screens.ServicesScreen
import dev.pocketserver.ui.screens.SettingsScreen
import dev.pocketserver.ui.screens.TerminalScreen
import dev.pocketserver.ui.theme.CodeFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    SERVICES("Services", Icons.Outlined.Dns),
    PROJECTS("Projects", Icons.Outlined.Folder),
    FILES("File manager", Icons.Outlined.Description),
    DATABASE("Databases", Icons.Outlined.Storage),
    TERMINAL("Terminal", Icons.Outlined.Terminal),
    LOGS("Logs", Icons.Outlined.Article),
    RUNTIME("Runtime manager", Icons.Outlined.Build),
    PORTS("Ports", Icons.Outlined.Lan),
    SETTINGS("Settings", Icons.Outlined.Settings),
    ABOUT("About", Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketServerApp(
    serverViewModel: ServerViewModel,
    factory: AppViewModelFactory,
) {
    val context = LocalContext.current
    val workspaceViewModel: WorkspaceViewModel = viewModel(factory = factory)
    val toolingViewModel: ToolingViewModel = viewModel(factory = factory)

    val settings by serverViewModel.settings.collectAsStateWithLifecycle()
    val services by serverViewModel.services.collectAsStateWithLifecycle()
    val definitions by serverViewModel.definitions.collectAsStateWithLifecycle()
    val serverRuntimes by serverViewModel.runtimes.collectAsStateWithLifecycle()
    val storage by serverViewModel.storage.collectAsStateWithLifecycle()
    val portStatus by serverViewModel.portStatus.collectAsStateWithLifecycle()
    val serverMessage by serverViewModel.message.collectAsStateWithLifecycle()

    val projects by workspaceViewModel.projects.collectAsStateWithLifecycle()
    val selectedProject by workspaceViewModel.selectedProject.collectAsStateWithLifecycle()
    val currentPath by workspaceViewModel.currentPath.collectAsStateWithLifecycle()
    val entries by workspaceViewModel.entries.collectAsStateWithLifecycle()
    val preview by workspaceViewModel.preview.collectAsStateWithLifecycle()
    val workspaceBusy by workspaceViewModel.busy.collectAsStateWithLifecycle()
    val workspaceMessage by workspaceViewModel.message.collectAsStateWithLifecycle()

    val runtimes by toolingViewModel.runtimes.collectAsStateWithLifecycle()
    val probe by toolingViewModel.probe.collectAsStateWithLifecycle()
    val toolingBusy by toolingViewModel.busy.collectAsStateWithLifecycle()
    val toolingMessage by toolingViewModel.message.collectAsStateWithLifecycle()
    val logFiles by toolingViewModel.logs.collectAsStateWithLifecycle()
    val selectedLog by toolingViewModel.selectedLog.collectAsStateWithLifecycle()
    val logContent by toolingViewModel.logContent.collectAsStateWithLifecycle()
    val terminalLines by toolingViewModel.terminalLines.collectAsStateWithLifecycle()
    val terminalRunning by toolingViewModel.terminalRunning.collectAsStateWithLifecycle()
    val databaseReadiness by toolingViewModel.databaseReadiness.collectAsStateWithLifecycle()
    val databaseBusy by toolingViewModel.databaseBusy.collectAsStateWithLifecycle()
    val provisioning by toolingViewModel.provisioning.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var destinationName by rememberSaveable { mutableStateOf(Destination.DASHBOARD.name) }
    val destination = Destination.valueOf(destinationName)

    fun navigate(target: Destination) {
        destinationName = target.name
        scope.launch { drawerState.close() }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch { snackbarHostState.showSnackbar("$label copied") }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(serverMessage) {
        serverMessage?.let {
            snackbarHostState.showSnackbar(it)
            serverViewModel.clearMessage()
        }
    }
    LaunchedEffect(workspaceMessage) {
        workspaceMessage?.let {
            snackbarHostState.showSnackbar(it)
            workspaceViewModel.clearMessage()
        }
    }
    LaunchedEffect(toolingMessage) {
        toolingMessage?.let {
            snackbarHostState.showSnackbar(it)
            toolingViewModel.clearMessage()
        }
    }

    BackHandler(enabled = destination != Destination.DASHBOARD) {
        destinationName = Destination.DASHBOARD.name
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 20.dp),
                ) {
                    Text(
                        text = "PocketServer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = CodeFont,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · arm64-v8a",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Destination.entries.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = item == destination,
                            onClick = { navigate(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(destination.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                serverViewModel.refresh()
                                workspaceViewModel.refreshProjects()
                                toolingViewModel.refreshRuntimes()
                                toolingViewModel.refreshLogs()
                                toolingViewModel.checkDatabases()
                            },
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh everything")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    Destination.DASHBOARD -> DashboardScreen(
                        settings = settings,
                        services = services,
                        definitions = definitions,
                        runtimes = serverRuntimes,
                        storage = storage,
                        onStartAll = serverViewModel::startAll,
                        onStopAll = serverViewModel::stopAll,
                        onStartService = serverViewModel::start,
                        onStopService = serverViewModel::stop,
                        onOpenServices = { navigate(Destination.SERVICES) },
                        onOpenRuntime = { navigate(Destination.RUNTIME) },
                        onCopyAddress = { copyToClipboard("Address", it) },
                    )

                    Destination.SERVICES -> ServicesScreen(
                        settings = settings,
                        services = services,
                        definitions = definitions,
                        onStart = serverViewModel::start,
                        onStop = serverViewModel::stop,
                        onRestart = serverViewModel::restart,
                        onOpenLogs = { kind ->
                            toolingViewModel.selectServiceLog(kind.id)
                            navigate(Destination.LOGS)
                        },
                        onOpenRuntime = { navigate(Destination.RUNTIME) },
                    )

                    Destination.PROJECTS -> ProjectsScreen(
                        projects = projects,
                        onRefresh = workspaceViewModel::refreshProjects,
                        onCreate = workspaceViewModel::createProject,
                        onDelete = workspaceViewModel::deleteProject,
                        onOpen = { name ->
                            workspaceViewModel.openProject(name)
                            navigate(Destination.FILES)
                        },
                    )

                    Destination.FILES -> FilesScreen(
                        project = selectedProject,
                        currentPath = currentPath,
                        entries = entries,
                        preview = preview,
                        busy = workspaceBusy,
                        onChooseProject = { navigate(Destination.PROJECTS) },
                        onNavigateUp = workspaceViewModel::navigateUp,
                        onOpenEntry = workspaceViewModel::openEntry,
                        onCreateFolder = workspaceViewModel::createFolder,
                        onCreateFile = workspaceViewModel::createFile,
                        onDeleteEntry = workspaceViewModel::deleteEntry,
                        onImport = workspaceViewModel::importDocument,
                        onExport = workspaceViewModel::exportPreview,
                        onSavePreview = workspaceViewModel::savePreview,
                        onClosePreview = workspaceViewModel::closePreview,
                    )

                    Destination.DATABASE -> DatabaseScreen(
                        settings = settings,
                        readiness = databaseReadiness,
                        busyEngine = databaseBusy,
                        onPrepare = toolingViewModel::prepareDatabase,
                        onRefresh = toolingViewModel::checkDatabases,
                        onCopyPassword = { engine ->
                            scope.launch {
                                val password = withContext(Dispatchers.IO) {
                                    toolingViewModel.databasePassword(engine)
                                }
                                if (password == null) {
                                    snackbarHostState.showSnackbar("Unable to read the ${engine.displayName} password")
                                } else {
                                    copyToClipboard("${engine.displayName} password", password)
                                }
                            }
                        },
                        onOpenPorts = { navigate(Destination.PORTS) },
                        onOpenRuntime = { navigate(Destination.RUNTIME) },
                    )

                    Destination.TERMINAL -> TerminalScreen(
                        lines = terminalLines,
                        running = terminalRunning,
                        onExecute = toolingViewModel::executeTerminal,
                        onClear = toolingViewModel::clearTerminal,
                    )

                    Destination.LOGS -> LogsScreen(
                        files = logFiles,
                        selected = selectedLog,
                        content = logContent,
                        onRefresh = toolingViewModel::refreshLogs,
                        onSelect = toolingViewModel::selectLog,
                        onDelete = toolingViewModel::deleteLog,
                        onCopy = { copyToClipboard("Log", it) },
                    )

                    Destination.RUNTIME -> RuntimeScreen(
                        runtimes = runtimes,
                        deviceAbis = probe.deviceAbis,
                        probeOutput = probe.probeOutput,
                        probeRunning = probe.probeRunning,
                        probeError = probe.probeError,
                        busy = toolingBusy,
                        message = toolingMessage,
                        autoRunning = provisioning.running,
                        autoMessage = provisioning.error ?: provisioning.message,
                        autoError = provisioning.error != null,
                        autoProgress = if (provisioning.total > 0) {
                            provisioning.completed.toFloat() / provisioning.total
                        } else {
                            null
                        },
                        onCheckBundles = toolingViewModel::checkForBundles,
                        onImport = toolingViewModel::importRuntime,
                        onRunProbe = toolingViewModel::runNativeProbe,
                        onUninstall = toolingViewModel::uninstallRuntime,
                        onRefresh = {
                            toolingViewModel.refreshRuntimes()
                            serverViewModel.loadRuntimes()
                        },
                    )

                    Destination.PORTS -> PortsScreen(
                        settings = settings,
                        status = portStatus,
                        message = serverMessage,
                        onRefresh = serverViewModel::checkPorts,
                        onApply = serverViewModel::applyPorts,
                    )

                    Destination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        versionName = BuildConfig.VERSION_NAME,
                        onSetTheme = serverViewModel::setTheme,
                        onSetWebServer = serverViewModel::setWebServer,
                        onSetLan = serverViewModel::setLanAccess,
                        onSetAutoRestart = serverViewModel::setAutoRestart,
                        onOpenPorts = { navigate(Destination.PORTS) },
                        onOpenAbout = { navigate(Destination.ABOUT) },
                        onOpenAppSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                    )

                    Destination.ABOUT -> AboutScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        deviceAbis = Build.SUPPORTED_ABIS.toList(),
                        targetSdk = BuildConfig.POCKET_TARGET_SDK,
                    )
                }
            }
        }
    }
}
