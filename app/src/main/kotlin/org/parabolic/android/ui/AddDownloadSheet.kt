package org.parabolic.android.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.parabolic.core.Container
import org.parabolic.core.MediaInfo
import org.parabolic.core.SponsorCategory
import org.parabolic.core.SubtitleFormat

private val HEIGHT_PRESETS = listOf<Int?>(null, 2160, 1440, 1080, 720, 480, 360)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDownloadSheet(
    draft: AddDownloadDraft,
    isInspecting: Boolean,
    inspectionError: String?,
    hasSavedCookies: Boolean,
    onUpdateDraft: ((AddDownloadDraft) -> AddDownloadDraft) -> Unit,
    onInspect: () -> Unit,
    onInspectPlaylistEntry: (String) -> Unit,
    onPickDestinationFolder: () -> Unit,
    onClearDestinationFolder: () -> Unit,
    onEnqueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showExplicitStreams by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(ParabolicIcons.ArrowBack, contentDescription = "Close Add Download")
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Add Download",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Inspect media metadata, select streams, or configure extraction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val actionLabel = when {
                    draft.inspectedMedia?.isCollection == true ->
                        "Enqueue (${draft.selectedEntryUrls.size})"
                    else -> "Download"
                }
                Button(
                    onClick = onEnqueue,
                    enabled = draft.urlInput.isNotBlank() && !isInspecting,
                ) {
                    Icon(ParabolicIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Media URL & Inspection Card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Media URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = draft.urlInput,
                            onValueChange = { text -> onUpdateDraft { it.copy(urlInput = text) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("HTTPS / HTTP Video, Audio, or Playlist URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = { onInspect() }),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val pasted = clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                            if (!pasted.isNullOrBlank()) {
                                                onUpdateDraft { it.copy(urlInput = pasted) }
                                            }
                                        }
                                    ) {
                                        Icon(ParabolicIcons.Paste, contentDescription = "Paste from clipboard")
                                    }
                                    if (draft.urlInput.isNotEmpty()) {
                                        IconButton(onClick = { onUpdateDraft { it.copy(urlInput = "", inspectedMedia = null) } }) {
                                            Icon(ParabolicIcons.Close, contentDescription = "Clear URL")
                                        }
                                    }
                                }
                            },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = draft.useCookies,
                                onClick = { onUpdateDraft { it.copy(useCookies = !it.useCookies) } },
                                label = {
                                    Text(
                                        if (hasSavedCookies) "Use Stored Cookies"
                                        else "Use Cookies (Import in Auth tab)"
                                    )
                                },
                                leadingIcon = {
                                    Icon(ParabolicIcons.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )

                            OutlinedButton(
                                onClick = onInspect,
                                enabled = draft.urlInput.isNotBlank() && !isInspecting,
                            ) {
                                if (isInspecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Inspecting...")
                                } else {
                                    Icon(ParabolicIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Inspect Formats")
                                }
                            }
                        }

                        if (!inspectionError.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = inspectionError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }

                // 2. Inspected Metadata / Playlist Selection Card
                draft.inspectedMedia?.let { media ->
                    InspectedMediaCard(
                        media = media,
                        selectedEntryUrls = draft.selectedEntryUrls,
                        onToggleEntry = { url -> onUpdateDraft { it.togglePlaylistEntry(url) } },
                        onSelectAll = { onUpdateDraft { it.selectAllPlaylistEntries() } },
                        onClearAll = { onUpdateDraft { it.clearPlaylistEntries() } },
                        onInspectSingleEntry = onInspectPlaylistEntry,
                    )
                }

                // 3. Container & Quality Card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Container & Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Video Containers (merged via MKV and recoded)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Container.entries.filter { !it.audioOnly }.forEach { c ->
                                FilterChip(
                                    selected = draft.container == c,
                                    onClick = { onUpdateDraft { it.withContainer(c) } },
                                    label = { Text(c.name) },
                                )
                            }
                        }

                        Text(
                            text = "Audio-Only Containers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Container.entries.filter { it.audioOnly }.forEach { c ->
                                FilterChip(
                                    selected = draft.container == c,
                                    onClick = { onUpdateDraft { it.withContainer(c) } },
                                    label = { Text(c.name) },
                                )
                            }
                        }

                        if (!draft.container.audioOnly) {
                            HorizontalDivider()
                            Text(
                                text = "Resolution Ceiling",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HEIGHT_PRESETS.forEach { height ->
                                    val label = when (height) {
                                        null -> "Best Available"
                                        2160 -> "2160p (4K)"
                                        1440 -> "1440p (2K)"
                                        1080 -> "1080p (FHD)"
                                        720 -> "720p (HD)"
                                        else -> "${height}p"
                                    }
                                    FilterChip(
                                        selected = draft.videoFormatId == null && draft.maxHeight == height,
                                        onClick = {
                                            onUpdateDraft { it.copy(maxHeight = height, videoFormatId = null) }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }

                        // Explicit stream & audio language pickers when metadata was inspected
                        val videoStreams = draft.videoFormats()
                        val audioStreams = draft.audioFormats()
                        val audioLanguages = draft.availableAudioLanguages()

                        if (audioLanguages.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = "Audio Track Language",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = draft.audioLanguage == null,
                                    onClick = { onUpdateDraft { it.copy(audioLanguage = null) } },
                                    label = { Text("Default Track") },
                                )
                                audioLanguages.forEach { lang ->
                                    FilterChip(
                                        selected = draft.audioLanguage == lang,
                                        onClick = { onUpdateDraft { it.copy(audioLanguage = lang) } },
                                        label = { Text(lang) },
                                    )
                                }
                            }
                        }

                        if (videoStreams.isNotEmpty() || audioStreams.isNotEmpty()) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Advanced Stream Selection (${videoStreams.size}V / ${audioStreams.size}A)",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                TextButton(onClick = { showExplicitStreams = !showExplicitStreams }) {
                                    Text(if (showExplicitStreams) "Hide Streams" else "Choose Exact Stream ID")
                                }
                            }
                            AnimatedVisibility(visible = showExplicitStreams) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (!draft.container.audioOnly && videoStreams.isNotEmpty()) {
                                        Text(
                                            text = "Explicit Video Stream (disables silent fallback)",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            FilterChip(
                                                selected = draft.videoFormatId == null,
                                                onClick = { onUpdateDraft { it.copy(videoFormatId = null) } },
                                                label = { Text("Auto (${draft.maxHeight?.let { "${it}p" } ?: "Best"})") },
                                            )
                                            videoStreams.take(16).forEach { fmt ->
                                                val res = fmt.height?.let { "${it}p" } ?: fmt.id
                                                val fps = fmt.fps?.toInt()?.let { " ${it}fps" }.orEmpty()
                                                val codec = fmt.videoCodec?.substringBefore('.')?.let { " $it" }.orEmpty()
                                                val size = fmt.bytes?.let { " (${ByteFormatter.formatBytes(it)})" }.orEmpty()
                                                FilterChip(
                                                    selected = draft.videoFormatId == fmt.id,
                                                    onClick = { onUpdateDraft { it.copy(videoFormatId = fmt.id) } },
                                                    label = { Text("${fmt.id}: $res$fps$codec$size") },
                                                )
                                            }
                                        }
                                    }

                                    if (audioStreams.isNotEmpty()) {
                                        Text(
                                            text = "Explicit Audio Stream",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            FilterChip(
                                                selected = draft.audioFormatId == null,
                                                onClick = { onUpdateDraft { it.copy(audioFormatId = null) } },
                                                label = { Text("Auto Best Audio") },
                                            )
                                            audioStreams.take(16).forEach { fmt ->
                                                val codec = fmt.audioCodec?.substringBefore('.') ?: fmt.extension.orEmpty()
                                                val kbps = fmt.bitrateKbps?.toInt()?.let { " ${it}k" }.orEmpty()
                                                val lang = fmt.audioLanguage?.let { " [$it]" }.orEmpty()
                                                FilterChip(
                                                    selected = draft.audioFormatId == fmt.id,
                                                    onClick = { onUpdateDraft { it.copy(audioFormatId = fmt.id) } },
                                                    label = { Text("${fmt.id}: $codec$kbps$lang") },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Time-Range Clipping & Chapters Card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Time Range & Chapters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trim Time Range (Clip)", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Download only a specific start/end section with keyframe cuts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.clipEnabled,
                                onCheckedChange = { enabled -> onUpdateDraft { it.withClipEnabled(enabled) } },
                            )
                        }

                        AnimatedVisibility(visible = draft.clipEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = draft.clipStartInput,
                                        onValueChange = { v -> onUpdateDraft { it.copy(clipStartInput = v) } },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("Start (MM:SS or sec)") },
                                        placeholder = { Text("00:00") },
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = draft.clipEndInput,
                                        onValueChange = { v -> onUpdateDraft { it.copy(clipEndInput = v) } },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("End (MM:SS or sec)") },
                                        placeholder = {
                                            Text(TimeParser.formatDuration(draft.inspectedMedia?.durationSeconds))
                                        },
                                        singleLine = true,
                                    )
                                }

                                val chapters = draft.inspectedMedia?.chapters.orEmpty()
                                if (chapters.isNotEmpty()) {
                                    Text(
                                        text = "Tap a chapter to fill clip bounds:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        chapters.take(12).forEach { chapter ->
                                            val startFmt = TimeParser.formatDuration(chapter.startSeconds)
                                            val endFmt = TimeParser.formatDuration(
                                                chapter.endSeconds ?: draft.inspectedMedia?.durationSeconds
                                            )
                                            AssistChip(
                                                onClick = {
                                                    onUpdateDraft {
                                                        it.copy(
                                                            clipStartInput = startFmt,
                                                            clipEndInput = if (endFmt != "--:--") endFmt else it.clipEndInput,
                                                        )
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = "${chapter.title} ($startFmt)",
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Split into Separate Chapter Files", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Mutually exclusive with clipping; outputs numbered chapter files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.splitChapters,
                                onCheckedChange = { enabled -> onUpdateDraft { it.withSplitChapters(enabled) } },
                            )
                        }
                    }
                }

                // 5. Subtitles Card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Subtitles & Captions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Download, convert, and embed or save sidecar subtitles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.subtitlesEnabled,
                                onCheckedChange = { enabled -> onUpdateDraft { it.copy(subtitlesEnabled = enabled) } },
                            )
                        }

                        AnimatedVisibility(visible = draft.subtitlesEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = draft.subtitleLanguagesInput,
                                    onValueChange = { text -> onUpdateDraft { it.copy(subtitleLanguagesInput = text) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Subtitle Languages (comma-separated, e.g. en, es)") },
                                    singleLine = true,
                                )

                                val tracks = draft.inspectedMedia?.subtitles.orEmpty()
                                if (tracks.isNotEmpty()) {
                                    Text(
                                        text = "Detected subtitle tracks:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        tracks.take(15).forEach { track ->
                                            val currentSet = draft.subtitleLanguagesInput
                                                .split(',', ' ')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .toSet()
                                            val selected = track.language in currentSet
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    val next = if (selected) currentSet - track.language else currentSet + track.language
                                                    onUpdateDraft {
                                                        it.copy(
                                                            subtitleLanguagesInput = next.joinToString(", "),
                                                            includeAutoSubtitles = it.includeAutoSubtitles || track.automatic,
                                                        )
                                                    }
                                                },
                                                label = {
                                                    Text(if (track.automatic) "${track.language} (auto)" else track.language)
                                                },
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Format:", style = MaterialTheme.typography.labelLarge)
                                    SubtitleFormat.entries.forEach { fmt ->
                                        FilterChip(
                                            selected = draft.subtitleFormat == fmt,
                                            onClick = { onUpdateDraft { it.copy(subtitleFormat = fmt) } },
                                            label = { Text(fmt.name) },
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Include Automatic Captions", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = draft.includeAutoSubtitles,
                                        onCheckedChange = { v -> onUpdateDraft { it.copy(includeAutoSubtitles = v) } },
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Embed Subtitles in Container", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = if (draft.supportsEmbeddedSubtitles) {
                                                "Supported for MP4 and MKV"
                                            } else {
                                                "Requires MP4 or MKV; sidecar subtitle files will be exported"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = draft.embedSubtitles && draft.supportsEmbeddedSubtitles,
                                        enabled = draft.supportsEmbeddedSubtitles,
                                        onCheckedChange = { v -> onUpdateDraft { it.copy(embedSubtitles = v) } },
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. SponsorBlock, Metadata & Destination Card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "SponsorBlock, Metadata & Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Remove SponsorBlock Segments",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SponsorCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = cat in draft.sponsorCategories,
                                    onClick = { onUpdateDraft { it.toggleSponsorCategory(cat) } },
                                    label = { Text(cat.name.replace('_', ' ')) },
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Embed Media Metadata & Chapters", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Writes title, uploader, date, and chapter markers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.embedMetadata,
                                onCheckedChange = { v -> onUpdateDraft { it.copy(embedMetadata = v) } },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Embed Cover Art / Thumbnail", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (draft.supportsCoverArt) "Supported for MP4, MKV, MP3, M4A, FLAC"
                                    else "Not supported by ${draft.container.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.embedThumbnail && draft.supportsCoverArt,
                                enabled = draft.supportsCoverArt,
                                onCheckedChange = { v -> onUpdateDraft { it.copy(embedThumbnail = v) } },
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Destination Folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = draft.destinationTreeUri
                                        ?: if (Build.VERSION.SDK_INT >= 29) "Downloads/Parabolic (MediaStore)"
                                        else "SAF folder required on Android 8/9",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (draft.destinationTreeUri != null && Build.VERSION.SDK_INT >= 29) {
                                    TextButton(onClick = onClearDestinationFolder) {
                                        Text("Default")
                                    }
                                }
                                OutlinedButton(onClick = onPickDestinationFolder) {
                                    Icon(ParabolicIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose Folder")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectedMediaCard(
    media: MediaInfo,
    selectedEntryUrls: Set<String>,
    onToggleEntry: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onInspectSingleEntry: (String) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val metaParts = buildList {
                media.uploader?.takeIf(String::isNotBlank)?.let { add(it) }
                if (!media.isCollection) {
                    add(TimeParser.formatDuration(media.durationSeconds))
                    add("${media.formats.size} streams")
                    if (media.chapters.isNotEmpty()) add("${media.chapters.size} chapters")
                    if (media.subtitles.isNotEmpty()) add("${media.subtitles.size} subtitle tracks")
                } else {
                    add("Collection: ${media.entries.size} available")
                    if (media.unavailableEntries > 0) add("${media.unavailableEntries} unavailable")
                }
            }
            Text(
                text = metaParts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (media.isCollection && media.entries.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Selected ${selectedEntryUrls.size} of ${media.entries.size} items",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row {
                        TextButton(onClick = onSelectAll) { Text("Select All") }
                        TextButton(onClick = onClearAll) { Text("Clear") }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    media.entries.forEach { entry ->
                        val entryUrl = entry.webpageUrl ?: return@forEach
                        val checked = entryUrl in selectedEntryUrls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleEntry(entryUrl) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleEntry(entryUrl) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = TimeParser.formatDuration(entry.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onInspectSingleEntry(entryUrl) }) {
                                Text("Formats")
                            }
                        }
                    }
                }
            }
        }
    }
}
