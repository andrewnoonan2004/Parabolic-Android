package org.parabolic.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.util.Consumer
import dagger.hilt.android.AndroidEntryPoint
import org.parabolic.android.ui.MainViewModel
import org.parabolic.android.ui.ParabolicAppShell
import org.parabolic.android.ui.ParabolicTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val newIntentListener = Consumer<Intent> { incoming ->
        handleIncomingIntent(incoming)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
        addOnNewIntentListener(newIntentListener)
        setContent {
            ParabolicTheme {
                ParabolicAppShell(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        removeOnNewIntentListener(newIntentListener)
        super.onDestroy()
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        val shared = when {
            incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain" ->
                incoming.getStringExtra(Intent.EXTRA_TEXT)
            incoming.hasExtra(EXTRA_SHARED_URL) ->
                incoming.getStringExtra(EXTRA_SHARED_URL)
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            viewModel.consumeIncomingText(shared)
        }
    }

    companion object {
        const val EXTRA_SHARED_URL = "org.parabolic.android.extra.SHARED_URL"
    }
}
