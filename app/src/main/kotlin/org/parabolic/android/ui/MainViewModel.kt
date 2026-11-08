package org.parabolic.android.ui

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.parabolic.android.data.DownloadEntity
import org.parabolic.android.data.DownloadLog
import org.parabolic.android.data.ExportEntity
import org.parabolic.android.data.PreferencesRepository
import org.parabolic.android.data.QueueRepository
import org.parabolic.android.data.QueueState
import org.parabolic.android.data.UserPreferences
import org.parabolic.android.storage.CookieStore
import org.parabolic.android.storage.NetscapeCookieFormatter
import org.parabolic.android.storage.OutputPublisher
import org.parabolic.core.DownloadSpec
import org.parabolic.core.InspectionOptions
import org.parabolic.core.MediaUrl
import org.parabolic.core.UpdateResult
import org.parabolic.core.YtDlpRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class TopDestination { DOWNLOADS, AUTH, SETTINGS }

enum class QueueFilter { ALL, ACTIVE, COMPLETED, ATTENTION }

data class QueueItemUi(
    val entity: DownloadEntity,
    val spec: DownloadSpec?,
    val title: String,
)

data class CookieStatusUi(
    val hasCookies: Boolean = false,
    val recordCount: Int = 0,
)

data class EngineStatusUi(
    val version: String? = null,
    val isUpdating: Boolean = false,
    val lastUpdateMessage: String? = null,
)

data class MainUiState(
    val destination: TopDestination = TopDestination.DOWNLOADS,
    val queueFilter: QueueFilter = QueueFilter.ALL,
    val items: List<QueueItemUi> = emptyList(),
    val selectedJobId: String? = null,
    val showAddDownload: Boolean = false,
    val draft: AddDownloadDraft = AddDownloadDraft(),
    val isInspecting: Boolean = false,
    val inspectionError: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val networkDraft: NetworkDraft = NetworkDraft(),
    val cookieStatus: CookieStatusUi = CookieStatusUi(),
    val showWebViewLogin: Boolean = false,
    val engineStatus: EngineStatusUi = EngineStatusUi(),
    val snackbarMessage: String? = null,
) {
    val filteredItems: List<QueueItemUi>
        get() = when (queueFilter) {
            QueueFilter.ALL -> items
            QueueFilter.ACTIVE -> items.filter {
                it.entity.state in setOf(QueueState.QUEUED, QueueState.DOWNLOADING, QueueState.EXPORTING, QueueState.PAUSED)
            }
            QueueFilter.COMPLETED -> items.filter { it.entity.state == QueueState.COMPLETED }
            QueueFilter.ATTENTION -> items.filter { it.entity.state in setOf(QueueState.FAILED, QueueState.CANCELED) }
        }

    val selectedItem: QueueItemUi?
        get() = items.firstOrNull { it.entity.id == selectedJobId }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ytDlp: YtDlpRepository,
    private val queue: QueueRepository,
    private val preferencesRepository: PreferencesRepository,
    private val cookieStore: CookieStore,
    private val outputPublisher: OutputPublisher,
    private val json: Json,
) : ViewModel() {

    private val titleCache = ConcurrentHashMap<String, String>()
    private var inspectJob: Job? = null
    private var networkDraftInitialized = false

    private val _localState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = combine(
        _localState,
        queue.downloads,
        preferencesRepository.preferences,
    ) { local, entities, prefs ->
        val mapped = entities.map { entity ->
            val spec = runCatching { json.decodeFromString<DownloadSpec>(entity.specification) }.getOrNull()
            val urlString = spec?.url?.value ?: entity.id
            val title = titleCache[urlString] ?: formatFallbackTitle(urlString)
            QueueItemUi(entity = entity, spec = spec, title = title)
        }
        val effectiveDraft = if (local.draft.destinationTreeUri == null && prefs.defaultFolderUri != null) {
            local.draft.copy(destinationTreeUri = prefs.defaultFolderUri)
        } else {
            local.draft
        }
        val selectedId = local.selectedJobId?.takeIf { id -> mapped.any { it.entity.id == id } }
            ?: mapped.firstOrNull()?.entity?.id
        local.copy(
            items = mapped,
            selectedJobId = selectedId,
            draft = effectiveDraft,
            preferences = prefs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { queue.reconcile() }
        }
        viewModelScope.launch {
            refreshCookieStatus()
        }
        viewModelScope.launch {
            refreshEngineVersion()
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                if (!networkDraftInitialized) {
                    networkDraftInitialized = true
                    _localState.update { it.copy(networkDraft = NetworkDraft.from(prefs.network)) }
                }
                val pending = prefs.pendingUrl
                if (!pending.isNullOrBlank()) {
                    preferencesRepository.clearSharedUrl()
                    consumeIncomingUrl(pending)
                }
            }
        }
    }

    fun setDestination(destination: TopDestination) {
        _localState.update { it.copy(destination = destination) }
    }

    fun setQueueFilter(filter: QueueFilter) {
        _localState.update { it.copy(queueFilter = filter) }
    }

    fun selectJob(jobId: String?) {
        _localState.update { it.copy(selectedJobId = jobId) }
    }

    fun openAddDownload() {
        val defaultFolder = uiState.value.preferences.defaultFolderUri
        val hasCookies = uiState.value.cookieStatus.hasCookies
        _localState.update { state ->
            state.copy(
                showAddDownload = true,
                inspectionError = null,
                draft = state.draft.copy(
                    destinationTreeUri = state.draft.destinationTreeUri ?: defaultFolder,
                    useCookies = state.draft.useCookies || hasCookies,
                ),
            )
        }
    }

    fun closeAddDownload() {
        inspectJob?.cancel()
        _localState.update {
            it.copy(
                showAddDownload = false,
                isInspecting = false,
                inspectionError = null,
            )
        }
    }

    fun resetAddDownloadDraft() {
        inspectJob?.cancel()
        val defaultFolder = uiState.value.preferences.defaultFolderUri
        val hasCookies = uiState.value.cookieStatus.hasCookies
        _localState.update {
            it.copy(
                isInspecting = false,
                inspectionError = null,
                draft = AddDownloadDraft(
                    destinationTreeUri = defaultFolder,
                    useCookies = hasCookies,
                ),
            )
        }
    }

    fun updateDraft(transform: (AddDownloadDraft) -> AddDownloadDraft) {
        _localState.update { it.copy(draft = transform(it.draft), inspectionError = null) }
    }

    fun consumeIncomingText(rawText: String) {
        val candidate = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
            .find(rawText.take(16384))
            ?.value
            ?: rawText.trim()
        val parsed = runCatching { MediaUrl.parse(candidate) }.getOrNull()
        if (parsed == null) {
            postMessage("Enter or share a valid HTTP/HTTPS URL")
            return
        }
        consumeIncomingUrl(parsed.value)
    }

    private fun consumeIncomingUrl(url: String) {
        val defaultFolder = uiState.value.preferences.defaultFolderUri
        val hasCookies = uiState.value.cookieStatus.hasCookies
        _localState.update { state ->
            state.copy(
                destination = TopDestination.DOWNLOADS,
                showAddDownload = true,
                inspectionError = null,
                draft = AddDownloadDraft(
                    urlInput = url,
                    destinationTreeUri = state.draft.destinationTreeUri ?: defaultFolder,
                    useCookies = state.draft.useCookies || hasCookies,
                ),
            )
        }
        inspectUrl(url)
    }

    fun inspectUrl(overrideUrl: String? = null) {
        val rawUrl = (overrideUrl ?: _localState.value.draft.urlInput).trim()
        val mediaUrl = runCatching { MediaUrl.parse(rawUrl) }.getOrElse { error ->
            _localState.update { it.copy(inspectionError = error.message ?: "Invalid URL") }
            return
        }
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _localState.update {
                it.copy(
                    isInspecting = true,
                    inspectionError = null,
                    draft = it.draft.copy(urlInput = mediaUrl.value),
                )
            }
            try {
                val state = uiState.value
                val info = ytDlp.getInfo(
                    url = mediaUrl,
                    options = InspectionOptions(
                        network = state.preferences.network,
                        useCookies = state.draft.useCookies,
                    ),
                )
                titleCache[mediaUrl.value] = info.title
                info.webpageUrl?.takeIf(String::isNotBlank)?.let { titleCache[it] = info.title }
                info.entries.forEach { entry ->
                    val entryUrl = entry.webpageUrl?.takeIf(String::isNotBlank)
                    if (entryUrl != null && entry.title.isNotBlank()) {
                        titleCache[entryUrl] = entry.title
                    }
                }
                _localState.update { current ->
                    current.copy(
                        isInspecting = false,
                        inspectionError = null,
                        draft = current.draft.withInspectedMedia(info),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _localState.update {
                    it.copy(
                        isInspecting = false,
                        inspectionError = error.message ?: "Could not inspect URL",
                    )
                }
            }
        }
    }

    fun inspectSingleEntryFromPlaylist(entryUrl: String) {
        _localState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    urlInput = entryUrl,
                    inspectedMedia = null,
                    selectedEntryUrls = emptySet(),
                ),
            )
        }
        inspectUrl(entryUrl)
    }

    fun enqueueCurrentDraft(
        requiresSafFolder: Boolean = Build.VERSION.SDK_INT < 29,
        onEnqueued: () -> Unit = {},
    ) {
        val current = uiState.value
        val specsResult = current.draft.toDownloadSpecs(
            defaultNetwork = current.preferences.network,
            requiresSafFolder = requiresSafFolder,
        )
        val specs = specsResult.getOrElse { error ->
            _localState.update { it.copy(inspectionError = error.message ?: "Invalid download options") }
            return
        }
        viewModelScope.launch {
            runCatching {
                var lastId: String? = null
                for (spec in specs) {
                    lastId = queue.enqueue(spec)
                }
                lastId
            }.onSuccess { lastId ->
                _localState.update {
                    it.copy(
                        showAddDownload = false,
                        selectedJobId = lastId ?: it.selectedJobId,
                        inspectionError = null,
                        draft = AddDownloadDraft(
                            destinationTreeUri = it.preferences.defaultFolderUri,
                            useCookies = it.cookieStatus.hasCookies,
                        ),
                    )
                }
                postMessage(
                    if (specs.size == 1) "Added download to queue"
                    else "Added ${specs.size} downloads to queue"
                )
                onEnqueued()
            }.onFailure { error ->
                _localState.update { it.copy(inspectionError = error.message ?: "Could not enqueue download") }
            }
        }
    }

    fun pauseJob(id: String) {
        viewModelScope.launch {
            runCatching { queue.pause(id) }
                .onFailure { postMessage(it.message ?: "Could not pause download") }
        }
    }

    fun resumeJob(id: String) {
        viewModelScope.launch {
            runCatching { queue.resume(id) }
                .onFailure { postMessage(it.message ?: "Could not resume download") }
        }
    }

    fun cancelJob(id: String) {
        viewModelScope.launch {
            runCatching { queue.cancel(id) }
                .onFailure { postMessage(it.message ?: "Could not cancel download") }
        }
    }

    fun removeJob(id: String) {
        viewModelScope.launch {
            runCatching { queue.remove(id) }
                .onFailure { postMessage(it.message ?: "Could not remove download") }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            runCatching { queue.clearCompleted() }
                .onSuccess { postMessage("Cleared completed history") }
                .onFailure { postMessage(it.message ?: "Could not clear history") }
        }
    }

    fun logsFor(id: String): Flow<List<DownloadLog>> = queue.logs(id)

    fun exportsFor(id: String): Flow<List<ExportEntity>> = queue.exports(id)

    fun onDestinationFolderPicked(uri: Uri, resultFlags: Int, setAsDefault: Boolean) {
        viewModelScope.launch {
            runCatching {
                outputPublisher.persistTreePermission(uri, resultFlags)
                val uriStr = uri.toString()
                if (setAsDefault) {
                    preferencesRepository.setDefaultFolderUri(uriStr)
                }
                _localState.update { state ->
                    state.copy(draft = state.draft.copy(destinationTreeUri = uriStr))
                }
            }.onSuccess {
                postMessage("Destination folder updated")
            }.onFailure { error ->
                postMessage(error.message ?: "Could not grant folder permission")
            }
        }
    }

    fun clearDefaultDestinationFolder() {
        viewModelScope.launch {
            preferencesRepository.setDefaultFolderUri(null)
            _localState.update { state ->
                state.copy(draft = state.draft.copy(destinationTreeUri = null))
            }
            postMessage("Reverted to system Downloads/CrestWave")
        }
    }

    fun importCookieFile(uri: Uri) {
        viewModelScope.launch {
            runCatching { cookieStore.import(uri) }
                .onSuccess {
                    refreshCookieStatus()
                    _localState.update { state -> state.copy(draft = state.draft.copy(useCookies = true)) }
                    postMessage("Imported Netscape cookies.txt")
                }
                .onFailure { error ->
                    postMessage(error.message ?: "Failed to import cookies")
                }
        }
    }

    fun openWebViewLogin() {
        _localState.update { it.copy(showWebViewLogin = true) }
    }

    fun closeWebViewLogin() {
        _localState.update { it.copy(showWebViewLogin = false) }
    }

    fun saveWebViewCookies(cookiesByUrl: Map<String, String>) {
        viewModelScope.launch {
            runCatching {
                val netscapeText = NetscapeCookieFormatter.format(cookiesByUrl)
                cookieStore.importNetscapeText(netscapeText)
            }.onSuccess {
                refreshCookieStatus()
                _localState.update {
                    it.copy(
                        showWebViewLogin = false,
                        draft = it.draft.copy(useCookies = true),
                    )
                }
                postMessage("Saved session cookies to private keyring")
            }.onFailure { error ->
                postMessage(error.message ?: "Could not capture session cookies")
            }
        }
    }

    fun clearCookies() {
        viewModelScope.launch {
            runCatching { cookieStore.clear() }
                .onSuccess {
                    refreshCookieStatus()
                    _localState.update { state -> state.copy(draft = state.draft.copy(useCookies = false)) }
                    postMessage("Cleared stored cookies")
                }
                .onFailure { error ->
                    postMessage(error.message ?: "Could not clear cookies")
                }
        }
    }

    fun setConcurrency(count: Int) {
        viewModelScope.launch {
            runCatching { preferencesRepository.setConcurrency(count.coerceIn(1, 4)) }
                .onFailure { postMessage(it.message ?: "Could not update concurrency") }
        }
    }

    fun updateNetworkDraft(transform: (NetworkDraft) -> NetworkDraft) {
        _localState.update { it.copy(networkDraft = transform(it.networkDraft)) }
    }

    fun saveNetworkSettings() {
        val draft = _localState.value.networkDraft
        val options = draft.toNetworkOptions().getOrElse { error ->
            postMessage(error.message ?: "Invalid network settings")
            return
        }
        viewModelScope.launch {
            runCatching { preferencesRepository.setNetwork(options) }
                .onSuccess { postMessage("Saved network settings") }
                .onFailure { postMessage(it.message ?: "Could not save network settings") }
        }
    }

    fun updateEngine() {
        if (_localState.value.engineStatus.isUpdating) return
        viewModelScope.launch {
            _localState.update {
                it.copy(engineStatus = it.engineStatus.copy(isUpdating = true, lastUpdateMessage = null))
            }
            runCatching { ytDlp.update() }
                .onSuccess { result ->
                    val ver = runCatching { ytDlp.version() }.getOrNull()
                    val msg = when (result) {
                        UpdateResult.UPDATED -> "yt-dlp updated to ${ver ?: "latest"}"
                        UpdateResult.CURRENT -> "yt-dlp is already up to date (${ver ?: "current"})"
                    }
                    _localState.update {
                        it.copy(engineStatus = EngineStatusUi(version = ver, isUpdating = false, lastUpdateMessage = msg))
                    }
                    postMessage(msg)
                }
                .onFailure { error ->
                    val msg = error.message ?: "Update failed"
                    _localState.update {
                        it.copy(engineStatus = it.engineStatus.copy(isUpdating = false, lastUpdateMessage = msg))
                    }
                    postMessage(msg)
                }
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun postMessage(message: String) {
        _snackbar.value = message
    }

    private suspend fun refreshCookieStatus() {
        val hasCookies = runCatching { cookieStore.hasCookies() }.getOrDefault(false)
        val count = if (hasCookies) runCatching { cookieStore.cookieCount() }.getOrDefault(0) else 0
        _localState.update {
            it.copy(cookieStatus = CookieStatusUi(hasCookies = hasCookies, recordCount = count))
        }
    }

    private suspend fun refreshEngineVersion() {
        val ver = runCatching { ytDlp.version() }.getOrNull()
        _localState.update {
            it.copy(engineStatus = it.engineStatus.copy(version = ver))
        }
    }

    companion object {
        fun formatFallbackTitle(urlString: String): String {
            return runCatching {
                val uri = java.net.URI(urlString)
                val host = uri.host?.removePrefix("www.").orEmpty()
                val path = uri.path?.trim('/')?.takeIf { it.isNotEmpty() }
                val query = uri.rawQuery?.takeIf { it.isNotEmpty() }
                when {
                    path != null && query != null -> "$host/$path?$query"
                    path != null -> "$host/$path"
                    host.isNotEmpty() -> host
                    else -> urlString
                }.take(96)
            }.getOrDefault(urlString.take(96))
        }
    }
}
