package org.parabolic.android.ui

import android.Manifest
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.parabolic.android.data.QueueState

@Composable
fun ParabolicAppShell(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarMessage by viewModel.snackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var folderPickSetsDefault by rememberSaveable { mutableStateOf(true) }
    var notificationPrompted by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // WorkManager can still run with reduced visibility if the user declines.
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            viewModel.onDestinationFolderPicked(
                uri = uri,
                resultFlags = flags,
                setAsDefault = folderPickSetsDefault,
            )
        }
    }

    val cookiePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importCookieFile(uri)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPrompted) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPrompted = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = state.destination != TopDestination.DOWNLOADS || state.showAddDownload) {
        if (state.showAddDownload) {
            viewModel.closeAddDownload()
        } else {
            viewModel.setDestination(TopDestination.DOWNLOADS)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                val clipData = dragEvent.clipData ?: return false
                if (clipData.itemCount <= 0) return false
                val text = clipData.getItemAt(0).coerceToText(context)?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    viewModel.consumeIncomingText(text)
                    return true
                }
                return false
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.isCtrlPressed && event.key == Key.N -> {
                            requestNotificationPermissionIfNeeded()
                            viewModel.setDestination(TopDestination.DOWNLOADS)
                            viewModel.openAddDownload()
                            true
                        }
                        event.key == Key.Escape && (state.showAddDownload || state.destination != TopDestination.DOWNLOADS) -> {
                            if (state.showAddDownload) viewModel.closeAddDownload()
                            else viewModel.setDestination(TopDestination.DOWNLOADS)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val mimeTypes = event.mimeTypes()
                    ClipDescription.MIMETYPE_TEXT_PLAIN in mimeTypes ||
                        ClipDescription.MIMETYPE_TEXT_URILIST in mimeTypes ||
                        ClipDescription.MIMETYPE_TEXT_HTML in mimeTypes
                },
                target = dragAndDropTarget,
            ),
        topBar = {
            ParabolicHeaderBar(
                destination = state.destination,
                showAddDownload = state.showAddDownload,
                hasCookies = state.cookieStatus.hasCookies,
                hasCompletedItems = state.items.any { it.entity.state == QueueState.COMPLETED },
                menuExpanded = menuExpanded,
                onOpenMenu = { menuExpanded = true },
                onDismissMenu = { menuExpanded = false },
                onAddClick = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.setDestination(TopDestination.DOWNLOADS)
                    viewModel.openAddDownload()
                },
                onNavigateHome = {
                    if (state.showAddDownload) {
                        viewModel.closeAddDownload()
                    } else {
                        viewModel.setDestination(TopDestination.DOWNLOADS)
                    }
                },
                onOpenKeyring = {
                    viewModel.closeAddDownload()
                    viewModel.setDestination(
                        if (state.destination == TopDestination.AUTH) TopDestination.DOWNLOADS
                        else TopDestination.AUTH
                    )
                },
                onOpenSettings = {
                    viewModel.closeAddDownload()
                    viewModel.setDestination(TopDestination.SETTINGS)
                },
                onUpdateEngine = viewModel::updateEngine,
                onClearCompleted = viewModel::clearCompleted,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state.destination) {
                TopDestination.DOWNLOADS -> {
                    DownloadsWorkspace(
                        state = state,
                        logsFlowProvider = viewModel::logsFor,
                        exportsFlowProvider = viewModel::exportsFor,
                        onOpenAddDownload = {
                            requestNotificationPermissionIfNeeded()
                            viewModel.openAddDownload()
                        },
                        onCloseAddDownload = viewModel::closeAddDownload,
                        onUpdateDraft = viewModel::updateDraft,
                        onInspectUrl = viewModel::inspectUrl,
                        onInspectPlaylistEntry = viewModel::inspectSingleEntryFromPlaylist,
                        onPickDestinationFolder = {
                            folderPickSetsDefault = false
                            folderPickerLauncher.launch(null)
                        },
                        onClearDestinationFolder = viewModel::clearDefaultDestinationFolder,
                        onEnqueueDraft = {
                            requestNotificationPermissionIfNeeded()
                            viewModel.enqueueCurrentDraft()
                        },
                        onSelectFilter = viewModel::setQueueFilter,
                        onSelectJob = viewModel::selectJob,
                        onPauseJob = viewModel::pauseJob,
                        onResumeJob = viewModel::resumeJob,
                        onCancelJob = viewModel::cancelJob,
                        onRemoveJob = viewModel::removeJob,
                        onClearCompleted = viewModel::clearCompleted,
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    )
                }
                TopDestination.AUTH -> {
                    AuthScreen(
                        cookieStatus = state.cookieStatus,
                        showWebViewLogin = state.showWebViewLogin,
                        onImportCookieFile = {
                            cookiePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                        },
                        onOpenWebViewLogin = viewModel::openWebViewLogin,
                        onCloseWebViewLogin = viewModel::closeWebViewLogin,
                        onSaveWebViewCookies = viewModel::saveWebViewCookies,
                        onClearCookies = viewModel::clearCookies,
                    )
                }
                TopDestination.SETTINGS -> {
                    SettingsScreen(
                        state = state,
                        onSetConcurrency = viewModel::setConcurrency,
                        onUpdateNetworkDraft = viewModel::updateNetworkDraft,
                        onSaveNetworkSettings = viewModel::saveNetworkSettings,
                        onPickDefaultFolder = {
                            folderPickSetsDefault = true
                            folderPickerLauncher.launch(null)
                        },
                        onClearDefaultFolder = viewModel::clearDefaultDestinationFolder,
                        onUpdateEngine = viewModel::updateEngine,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParabolicHeaderBar(
    destination: TopDestination,
    showAddDownload: Boolean,
    hasCookies: Boolean,
    hasCompletedItems: Boolean,
    menuExpanded: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onAddClick: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenKeyring: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateEngine: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Left action: Expressive M3 "+ Add" tonal pill button on homepage, or "<- Back" inside subviews
                if (destination == TopDestination.DOWNLOADS && !showAddDownload) {
                    FilledTonalButton(
                        onClick = onAddClick,
                        modifier = Modifier.align(Alignment.CenterStart),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            ParabolicIcons.Add,
                            contentDescription = "Add Download",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onNavigateHome,
                        modifier = Modifier.align(Alignment.CenterStart),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            ParabolicIcons.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Centered title: "Parabolic"
                val headerTitle = when {
                    showAddDownload && destination == TopDestination.DOWNLOADS -> "Add Download"
                    destination == TopDestination.AUTH -> "Keyring"
                    destination == TopDestination.SETTINGS -> "Preferences"
                    else -> "Parabolic"
                }
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Right actions: Expressive M3 tonal icon buttons for Keyring + Menu
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenKeyring,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (destination == TopDestination.AUTH) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            contentColor = if (destination == TopDestination.AUTH) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasCookies) {
                                    Badge()
                                }
                            }
                        ) {
                            Icon(
                                ParabolicIcons.Key,
                                contentDescription = "Keyring",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Box {
                        FilledTonalIconButton(
                            onClick = onOpenMenu,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (destination == TopDestination.SETTINGS || menuExpanded) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                },
                                contentColor = if (destination == TopDestination.SETTINGS || menuExpanded) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Icon(
                                ParabolicIcons.Menu,
                                contentDescription = "Main Menu",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = onDismissMenu,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Download") },
                                leadingIcon = { Icon(ParabolicIcons.Add, contentDescription = null) },
                                onClick = {
                                    onDismissMenu()
                                    onAddClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Keyring") },
                                leadingIcon = { Icon(ParabolicIcons.Key, contentDescription = null) },
                                onClick = {
                                    onDismissMenu()
                                    onOpenKeyring()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Preferences") },
                                leadingIcon = { Icon(ParabolicIcons.Settings, contentDescription = null) },
                                onClick = {
                                    onDismissMenu()
                                    onOpenSettings()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Check for yt-dlp Update") },
                                leadingIcon = { Icon(ParabolicIcons.Refresh, contentDescription = null) },
                                onClick = {
                                    onDismissMenu()
                                    onUpdateEngine()
                                },
                            )
                            if (hasCompletedItems) {
                                DropdownMenuItem(
                                    text = { Text("Clear Completed Downloads") },
                                    leadingIcon = { Icon(ParabolicIcons.Delete, contentDescription = null) },
                                    onClick = {
                                        onDismissMenu()
                                        onClearCompleted()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
