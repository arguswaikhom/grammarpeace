package com.grammarpeace.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grammarpeace.app.R
import com.grammarpeace.app.core.WritingAction
import com.grammarpeace.app.ui.theme.GrammarPeaceTheme
import kotlinx.coroutines.launch

/** The app itself: paste or type text, pick an action, read the result. Settings live here too. */
class MainActivity : ComponentActivity() {

    private val viewModel: WritingViewModel by viewModels { WritingViewModel.Factory }

    private var showSettings by mutableStateOf(false)

    /** Set when the app was opened from the Quick Settings tile or the launcher shortcut. */
    private var clipboardCheckPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showSettings = savedInstanceState?.getBoolean(STATE_SETTINGS) ?: false
        if (savedInstanceState == null) handle(intent)

        setContent {
            GrammarPeaceTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // The clipboard can only be read once the window has focus, which is after onResume.
                val focused = LocalWindowInfo.current.isWindowFocused
                LaunchedEffect(clipboardCheckPending, focused) {
                    if (clipboardCheckPending && focused) {
                        clipboardCheckPending = false
                        val text = readClipboardText()
                        if (text == null) {
                            snackbar.showSnackbar(getString(R.string.clipboard_empty))
                        } else {
                            viewModel.setInput(text)
                            viewModel.run(WritingAction.Fix)
                        }
                    }
                }

                if (showSettings) {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
                    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        state = settingsState,
                        onEdit = settingsViewModel::edit,
                        onTest = settingsViewModel::test,
                        onOpenLink = ::openLink,
                        onBack = { showSettings = false },
                    )
                } else {
                    EditorScreen(
                        state = state,
                        snackbar = snackbar,
                        onInputChange = viewModel::setInput,
                        onPaste = {
                            val text = readClipboardText()
                            if (text == null) {
                                scope.launch { snackbar.showSnackbar(getString(R.string.clipboard_empty)) }
                            } else {
                                viewModel.setInput(text)
                            }
                        },
                        onClear = viewModel::clear,
                        onAction = viewModel::run,
                        onCopy = ::copyToClipboard,
                        onShare = ::share,
                        onUseAsInput = viewModel::setInput,
                        onRetry = viewModel::retry,
                        onOpenSettings = { showSettings = true },
                        onToggleChanges = viewModel::toggleChanges,
                        onLoadAlternatives = viewModel::loadAlternatives,
                        onLoadExplanation = viewModel::loadExplanation,
                        onChoose = viewModel::choose,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SETTINGS, showSettings)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_CHECK_CLIPBOARD -> {
                showSettings = false
                clipboardCheckPending = true
            }
            ACTION_OPEN_SETTINGS -> showSettings = true
        }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            // No browser installed, nothing useful to do.
        }
    }

    companion object {
        const val ACTION_CHECK_CLIPBOARD = "com.grammarpeace.app.action.CHECK_CLIPBOARD"
        const val ACTION_OPEN_SETTINGS = "com.grammarpeace.app.action.OPEN_SETTINGS"
        private const val STATE_SETTINGS = "showSettings"

        fun checkClipboardIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_CHECK_CLIPBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun settingsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
