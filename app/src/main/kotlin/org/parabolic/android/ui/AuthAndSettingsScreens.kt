package org.parabolic.android.ui

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AuthScreen(
    cookieStatus: CookieStatusUi,
    showWebViewLogin: Boolean,
    onImportCookieFile: () -> Unit,
    onOpenWebViewLogin: () -> Unit,
    onCloseWebViewLogin: () -> Unit,
    onSaveWebViewCookies: (Map<String, String>) -> Unit,
    onClearCookies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Authentication & Keyring",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Authenticate age-restricted or subscriber media using an imported Netscape cookies.txt file or by signing in through the isolated HTTPS WebView.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        ParabolicIcons.Key,
                        contentDescription = null,
                        tint = if (cookieStatus.hasCookies) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            text = if (cookieStatus.hasCookies) "Credentials Stored in Private Keyring"
                            else "No Credentials Stored",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (cookieStatus.hasCookies) {
                                "${cookieStatus.recordCount} Netscape cookie records ready for isolated per-job snapshots"
                            } else {
                                "Import a cookies.txt file or sign in with WebView below"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onImportCookieFile,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(ParabolicIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import cookies.txt")
                    }
                    OutlinedButton(
                        onClick = onOpenWebViewLogin,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(ParabolicIcons.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WebView Sign-In")
                    }
                }

                if (cookieStatus.hasCookies) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                WebStorage.getInstance().deleteAllData()
                            }
                            onClearCookies()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(ParabolicIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Stored Cookies & WebView Session")
                    }
                }
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Security & Privacy Architecture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "• Cookies are stored exclusively in internal noBackupFilesDir and excluded from cloud/device backups.\n" +
                        "• No cookie contents or credentials are ever written to Room, DataStore, or process logs.\n" +
                        "• Each concurrent yt-dlp inspection or download receives a private temporary snapshot so parallel jobs never corrupt the cookie jar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showWebViewLogin) {
        WebViewLoginDialog(
            onDismiss = onCloseWebViewLogin,
            onSaveCookies = onSaveWebViewCookies,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLoginDialog(
    onDismiss: () -> Unit,
    onSaveCookies: (Map<String, String>) -> Unit,
) {
    var addressInput by rememberSaveable { mutableStateOf("https://accounts.google.com") }
    var currentUrl by rememberSaveable { mutableStateOf("https://accounts.google.com") }
    val visitedUrls = remember { mutableStateListOf("https://accounts.google.com") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(ParabolicIcons.Close, contentDescription = "Close WebView")
                    }
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("HTTPS Sign-In URL") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val normalized = normalizeHttpsUrl(addressInput)
                                addressInput = normalized
                                currentUrl = normalized
                                webViewRef?.loadUrl(normalized)
                            }
                        ),
                    )
                    Button(
                        onClick = {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.flush()
                            val targets = (visitedUrls + listOf(currentUrl, addressInput))
                                .map { normalizeHttpsUrl(it) }
                                .distinct()
                            val captured = LinkedHashMap<String, String>()
                            for (url in targets) {
                                val cookie = runCatching { cookieManager.getCookie(url) }.getOrNull()
                                if (!cookie.isNullOrBlank()) {
                                    captured[url] = cookie
                                }
                            }
                            onSaveCookies(captured)
                        }
                    ) {
                        Text("Save Cookies")
                    }
                }

                HorizontalDivider()

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url ?: return false
                                    return !target.scheme.equals("https", ignoreCase = true)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!url.isNullOrBlank() && url.startsWith("https://", ignoreCase = true)) {
                                        currentUrl = url
                                        addressInput = url
                                        if (url !in visitedUrls) visitedUrls.add(url)
                                    }
                                }
                            }
                            loadUrl(currentUrl)
                            webViewRef = this
                        }
                    },
                )

                DisposableEffect(Unit) {
                    onDispose {
                        webViewRef?.apply {
                            stopLoading()
                            clearCache(true)
                            clearHistory()
                            destroy()
                        }
                        webViewRef = null
                    }
                }
            }
        }
    }
}

private fun normalizeHttpsUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://")
        trimmed.isNotEmpty() -> "https://$trimmed"
        else -> "https://accounts.google.com"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onSetConcurrency: (Int) -> Unit,
    onUpdateNetworkDraft: ((NetworkDraft) -> NetworkDraft) -> Unit,
    onSaveNetworkSettings: () -> Unit,
    onPickDefaultFolder: () -> Unit,
    onClearDefaultFolder: () -> Unit,
    onUpdateEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val prefs = state.preferences
    val netDraft = state.networkDraft
    val engine = state.engineStatus

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings & Extraction Engine",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // 1. Concurrency & Queue Execution
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Concurrent Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Maximum simultaneous active download workers (1–4). Lowering concurrency allows active transfers to finish before starting new slots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..4).forEach { count ->
                        FilterChip(
                            selected = prefs.maxConcurrent == count,
                            onClick = { onSetConcurrency(count) },
                            label = { Text("$count ${if (count == 1) "Job" else "Jobs"}") },
                        )
                    }
                }
            }
        }

        // 2. Default Storage Folder
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Default Destination Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = prefs.defaultFolderUri
                        ?: if (Build.VERSION.SDK_INT >= 29) "Downloads/CrestWave (MediaStore)"
                        else "SAF folder required on Android 8 and 9",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Native tools stage files in private internal storage and atomically publish completed outputs to MediaStore or your chosen SAF folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickDefaultFolder) {
                        Icon(ParabolicIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select SAF Folder")
                    }
                    if (prefs.defaultFolderUri != null && Build.VERSION.SDK_INT >= 29) {
                        OutlinedButton(onClick = onClearDefaultFolder) {
                            Text("Use System Downloads")
                        }
                    }
                }
            }
        }

        // 3. Network, Rate Limit, Proxy & aria2c
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Network, Proxy & aria2c",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use aria2c Multi-Connection Downloader", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Accelerates segmented transfers; automatically disabled when a rate limit is set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = netDraft.useAria2,
                        onCheckedChange = { enabled -> onUpdateNetworkDraft { it.withAria2(enabled) } },
                    )
                }

                OutlinedTextField(
                    value = netDraft.speedLimitKbpsInput,
                    onValueChange = { text -> onUpdateNetworkDraft { it.withSpeedLimit(text) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Speed Limit (KB/s, leave blank for unlimited)") },
                    placeholder = { Text("e.g. 2048 for 2 MB/s") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                OutlinedTextField(
                    value = netDraft.proxyInput,
                    onValueChange = { text -> onUpdateNetworkDraft { it.copy(proxyInput = text) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Proxy URL (http, https, socks4, socks5, socks5h)") },
                    placeholder = { Text("socks5://127.0.0.1:1080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                Button(onClick = onSaveNetworkSettings) {
                    Text("Apply Network Settings")
                }
            }
        }

        // 4. Bundled yt-dlp / FFmpeg Engine & Updater
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Extraction Engine (yt-dlp + FFmpeg + aria2c)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Installed yt-dlp version: ${engine.version ?: "Bundled (initializing on first run)"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!engine.lastUpdateMessage.isNullOrBlank()) {
                    Text(
                        text = engine.lastUpdateMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Engine updates are serialized with active downloads and inspections so the binary is never overwritten mid-execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUpdateEngine,
                    enabled = !engine.isUpdating,
                ) {
                    if (engine.isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Checking for Updates...")
                    } else {
                        Icon(ParabolicIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update yt-dlp Engine")
                    }
                }
            }
        }
    }
}
