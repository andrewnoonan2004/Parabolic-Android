package org.parabolic.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.parabolic.android.data.DownloadLog
import org.parabolic.android.data.ExportEntity
import org.parabolic.android.data.QueueState
import org.parabolic.core.Destination

private const val HERO_ADD_DOWNLOAD_KEY = "hero_add_download_bounds"

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun DownloadsWorkspace(
    state: MainUiState,
    logsFlowProvider: (String) -> Flow<List<DownloadLog>>,
    exportsFlowProvider: (String) -> Flow<List<ExportEntity>>,
    onOpenAddDownload: () -> Unit,
    onCloseAddDownload: () -> Unit,
    onUpdateDraft: ((AddDownloadDraft) -> AddDownloadDraft) -> Unit,
    onInspectUrl: () -> Unit,
    onInspectPlaylistEntry: (String) -> Unit,
    onPickDestinationFolder: () -> Unit,
    onClearDestinationFolder: () -> Unit,
    onEnqueueDraft: () -> Unit,
    onSelectFilter: (QueueFilter) -> Unit,
    onSelectJob: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.showAddDownload,
            label = "AddDownloadHeroTransition",
        ) { showingAddSheet ->
            if (showingAddSheet) {
                AddDownloadSheet(
                    draft = state.draft,
                    isInspecting = state.isInspecting,
                    inspectionError = state.inspectionError,
                    hasSavedCookies = state.cookieStatus.hasCookies,
                    onUpdateDraft = onUpdateDraft,
                    onInspect = onInspectUrl,
                    onInspectPlaylistEntry = onInspectPlaylistEntry,
                    onPickDestinationFolder = onPickDestinationFolder,
                    onClearDestinationFolder = onClearDestinationFolder,
                    onEnqueue = onEnqueueDraft,
                    onDismiss = onCloseAddDownload,
                    modifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = HERO_ADD_DOWNLOAD_KEY),
                        animatedVisibilityScope = this@AnimatedContent,
                    ),
                )
            } else if (state.items.isEmpty()) {
                ParabolicEmptyHomepage(
                    heroButtonModifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = HERO_ADD_DOWNLOAD_KEY),
                        animatedVisibilityScope = this@AnimatedContent,
                    ),
                    onAddDownloadClick = onOpenAddDownload,
                )
            } else {
                NavigableListDetailPaneScaffold(
                    navigator = navigator,
                    listPane = {
                        AnimatedPane {
                            QueueListPane(
                                state = state,
                                heroModifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = HERO_ADD_DOWNLOAD_KEY),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                                onOpenAddDownload = onOpenAddDownload,
                                onSelectFilter = onSelectFilter,
                                onSelectItem = { id ->
                                    onSelectJob(id)
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                                    }
                                },
                                onPauseJob = onPauseJob,
                                onResumeJob = onResumeJob,
                                onCancelJob = onCancelJob,
                                onRemoveJob = onRemoveJob,
                                onClearCompleted = onClearCompleted,
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val selectedId = navigator.currentDestination?.contentKey ?: state.selectedJobId
                            val selectedItem = state.items.firstOrNull { it.entity.id == selectedId } ?: state.selectedItem
                            QueueDetailPane(
                                item = selectedItem,
                                canNavigateBack = navigator.canNavigateBack(),
                                onNavigateBack = {
                                    scope.launch { navigator.navigateBack() }
                                },
                                logsFlowProvider = logsFlowProvider,
                                exportsFlowProvider = exportsFlowProvider,
                                onPauseJob = onPauseJob,
                                onResumeJob = onResumeJob,
                                onCancelJob = onCancelJob,
                                onRemoveJob = onRemoveJob,
                                onMessage = onMessage,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ParabolicEmptyHomepage(
    heroButtonModifier: Modifier,
    onAddDownloadClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ParabolicDishEmblem(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable(onClick = onAddDownloadClick),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Download Media",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Add a video, audio, or playlist URL to start downloading",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                FilledTonalButton(
                    onClick = onAddDownloadClick,
                    modifier = heroButtonModifier,
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Icon(
                        ParabolicIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add Download",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueListPane(
    state: MainUiState,
    heroModifier: Modifier,
    onOpenAddDownload: () -> Unit,
    onSelectFilter: (QueueFilter) -> Unit,
    onSelectItem: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onClearCompleted: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero Add Download Card
        ElevatedCard(
            modifier = heroModifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAddDownload),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add New Download",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Paste, share, or drop a video, audio, or playlist URL (Ctrl+N)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onOpenAddDownload) {
                    Icon(ParabolicIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add URL")
                }
            }
        }

        // Queue filter bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                QueueFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        QueueFilter.ALL -> "All (${state.items.size})"
                        QueueFilter.ACTIVE -> "Active"
                        QueueFilter.COMPLETED -> "Completed"
                        QueueFilter.ATTENTION -> "Failed / Canceled"
                    }
                    FilterChip(
                        selected = state.queueFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(label) },
                    )
                }
            }
            if (state.items.any { it.entity.state == QueueState.COMPLETED }) {
                TextButton(onClick = onClearCompleted) {
                    Text("Clear Done")
                }
            }
        }

        val items = state.filteredItems
        if (items.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            ParabolicIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (state.items.isEmpty()) "Your download queue is empty"
                            else "No downloads match this filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Tap \"Add URL\" above or share a link from your browser to inspect formats, chapters, subtitles, and SponsorBlock segments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.entity.id }) { item ->
                    QueueItemCard(
                        item = item,
                        isSelected = item.entity.id == state.selectedJobId,
                        onClick = { onSelectItem(item.entity.id) },
                        onPause = { onPauseJob(item.entity.id) },
                        onResume = { onResumeJob(item.entity.id) },
                        onCancel = { onCancelJob(item.entity.id) },
                        onRemove = { onRemoveJob(item.entity.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItemUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val entity = item.entity
    val spec = item.spec
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    spec?.url?.value?.let { rawUrl ->
                        Text(
                            text = rawUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    spec?.options?.container?.let { container ->
                        BadgePill(
                            text = container.name,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    StateBadge(state = entity.state)
                }
            }

            if (entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING, QueueState.EXPORTING)) {
                val pct = entity.percent?.coerceIn(0f, 100f)
                if (pct != null && entity.state == QueueState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }

            val stats = buildList {
                entity.percent?.let { add("${it.toInt()}%") }
                val speed = ByteFormatter.formatSpeed(entity.speed)
                if (speed.isNotEmpty()) add(speed)
                entity.eta?.takeIf { it >= 0 }?.let { add("ETA ${TimeParser.formatDuration(it.toDouble())}") }
            }
            if (stats.isNotEmpty()) {
                Text(
                    text = stats.joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!entity.error.isNullOrBlank()) {
                Text(
                    text = entity.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING)) {
                    TextButton(onClick = onPause) {
                        Icon(ParabolicIcons.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                }
                if (entity.state in setOf(QueueState.PAUSED, QueueState.CANCELED, QueueState.FAILED)) {
                    TextButton(onClick = onResume) {
                        Icon(ParabolicIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (entity.state == QueueState.PAUSED) "Resume" else "Retry")
                    }
                }
                if (entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING, QueueState.EXPORTING, QueueState.PAUSED)) {
                    TextButton(onClick = onCancel) {
                        Icon(ParabolicIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(ParabolicIcons.Delete, contentDescription = "Remove from queue")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueDetailPane(
    item: QueueItemUi?,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    logsFlowProvider: (String) -> Flow<List<DownloadLog>>,
    exportsFlowProvider: (String) -> Flow<List<ExportEntity>>,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var logQuery by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 1.dp,
    ) {
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Select a download item to inspect its configuration, exported media files, and live yt-dlp log.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }

        val entity = item.entity
        val spec = item.spec
        val logs by remember(entity.id) { logsFlowProvider(entity.id) }.collectAsState(initial = emptyList())
        val exports by remember(entity.id) { exportsFlowProvider(entity.id) }.collectAsState(initial = emptyList())

        val filteredLogs = remember(logs, logQuery) {
            if (logQuery.isBlank()) logs
            else logs.filter { it.line.contains(logQuery.trim(), ignoreCase = true) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (canNavigateBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(ParabolicIcons.ArrowBack, contentDescription = "Back to Queue")
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = spec?.url?.value ?: entity.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StateBadge(state = entity.state)
            }

            // Action Controls
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING)) {
                    OutlinedButton(onClick = { onPauseJob(entity.id) }) {
                        Icon(ParabolicIcons.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }
                }
                if (entity.state in setOf(QueueState.PAUSED, QueueState.CANCELED, QueueState.FAILED)) {
                    Button(onClick = { onResumeJob(entity.id) }) {
                        Icon(ParabolicIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (entity.state == QueueState.PAUSED) "Resume" else "Retry")
                    }
                }
                if (entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING, QueueState.EXPORTING, QueueState.PAUSED)) {
                    OutlinedButton(onClick = { onCancelJob(entity.id) }) {
                        Icon(ParabolicIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
                OutlinedButton(onClick = { onRemoveJob(entity.id) }) {
                    Icon(ParabolicIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }

            // Specification Summary Card
            if (spec != null) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val opts = spec.options
                        val summaryParts = buildList {
                            add("Container: ${opts.container.name}")
                            opts.maxHeight?.let { add("Max ${it}p") }
                            opts.videoFormatId?.let { add("Video ID: $it") }
                            opts.audioFormatId?.let { add("Audio ID: $it") }
                            opts.audioLanguage?.let { add("Audio Lang: $it") }
                            opts.clip?.let {
                                add("Clip: ${TimeParser.formatDuration(it.startSeconds)}–${TimeParser.formatDuration(it.endSeconds)}")
                            }
                            if (opts.splitChapters) add("Split Chapters")
                            opts.subtitles?.let {
                                add("Subs: ${it.languages.joinToString(",")} (${it.format.name})")
                            }
                            if (opts.sponsorCategories.isNotEmpty()) {
                                add("SponsorBlock: ${opts.sponsorCategories.size} categories")
                            }
                            if (opts.network.useAria2) add("aria2c")
                            if (opts.useCookies) add("Authenticated")
                        }
                        Text(
                            text = summaryParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val destText = when (val d = spec.destination) {
                            Destination.Downloads -> "Destination: Downloads/CrestWave"
                            is Destination.Tree -> "Destination: ${d.uri}"
                        }
                        Text(
                            text = destText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Published / Exported Files
            if (exports.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Exported Media (${exports.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        exports.forEach { exported ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = exported.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AssistChip(
                                        onClick = {
                                            runCatching {
                                                val uri = exported.uri.toUri()
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(Intent.createChooser(openIntent, exported.fileName))
                                            }.onFailure { onMessage("No app available to open this file") }
                                        },
                                        label = { Text("Open") },
                                        leadingIcon = {
                                            Icon(ParabolicIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                    )
                                    AssistChip(
                                        onClick = {
                                            runCatching {
                                                val uri = exported.uri.toUri()
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = context.contentResolver.getType(uri) ?: "*/*"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, exported.fileName))
                                            }.onFailure { onMessage("Could not share file") }
                                        },
                                        label = { Text("Share") },
                                        leadingIcon = {
                                            Icon(ParabolicIcons.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Sanitized Log Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sanitized Process Log (${logs.size} lines)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = filteredLogs.joinToString("\n") { it.line }
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Parabolic Log", text))
                        onMessage("Copied ${filteredLogs.size} log lines to clipboard")
                    },
                    enabled = filteredLogs.isNotEmpty(),
                ) {
                    Icon(ParabolicIcons.Paste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Log")
                }
            }

            OutlinedTextField(
                value = logQuery,
                onValueChange = { logQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter log output...") },
                singleLine = true,
                leadingIcon = { Icon(ParabolicIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (logQuery.isNotEmpty()) {
                        IconButton(onClick = { logQuery = "" }) {
                            Icon(ParabolicIcons.Close, contentDescription = "Clear filter")
                        }
                    }
                },
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (logs.isEmpty()) "No log lines emitted yet." else "No log lines match filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredLogs, key = { it.sequence }) { entry ->
                            Text(
                                text = entry.line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: QueueState) {
    val (bg, fg) = when (state) {
        QueueState.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QueueState.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        QueueState.DOWNLOADING, QueueState.EXPORTING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    BadgePill(text = state.name, containerColor = bg, contentColor = fg)
}

@Composable
private fun BadgePill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}
