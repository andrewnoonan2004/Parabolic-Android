package org.parabolic.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.parabolic.android.data.PreferencesRepository
import org.parabolic.core.MediaUrl
import javax.inject.Inject

/** Persists shared URLs and forwards the user directly into MainActivity's Add Download sheet. */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    @Inject lateinit var preferences: PreferencesRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(16384)
            val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value
                ?.let { runCatching { MediaUrl.parse(it) }.getOrNull() }
            if (url != null) {
                applicationScope.launch { runCatching { preferences.saveSharedUrl(url.value) } }
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(MainActivity.EXTRA_SHARED_URL, url.value)
                    }
                )
            }
        }
        finish()
    }
    private companion object { val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
