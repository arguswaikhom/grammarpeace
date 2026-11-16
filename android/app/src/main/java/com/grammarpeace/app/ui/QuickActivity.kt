package com.grammarpeace.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grammarpeace.app.R
import com.grammarpeace.app.ui.components.ActionRow
import com.grammarpeace.app.ui.components.ResultSection
import com.grammarpeace.app.ui.theme.GrammarPeaceTheme

/**
 * A sheet that opens over whatever app the user is in. Reached two ways:
 *
 * - the text selection menu (ACTION_PROCESS_TEXT). If the text is editable the result can be
 *   written straight back with Replace.
 * - the share sheet (ACTION_SEND). Read-only, so the result is copied.
 *
 * Grammar is fixed straight away, since that is what the user most likely came for.
 */
class QuickActivity : ComponentActivity() {

    private val viewModel: WritingViewModel by viewModels { WritingViewModel.Factory }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val request = QuickRequest.from(intent)
        if (request == null) {
            finish()
            return
        }
        viewModel.startWith(request.text)

        setContent {
            GrammarPeaceTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                ModalBottomSheet(
                    onDismissRequest = ::finish,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = ::openSettings) {
                                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                            }
                        }

                        Text(
                            text = state.input.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )

                        ActionRow(enabled = true, translateTo = state.translateTo, onAction = viewModel::run)

                        ResultSection(
                            state = state,
                            onCopy = { text ->
                                copyToClipboard(text)
                                finish()
                            },
                            onRetry = viewModel::retry,
                            onOpenSettings = ::openSettings,
                            onToggleChanges = viewModel::toggleChanges,
                            onLoadAlternatives = viewModel::loadAlternatives,
                            onLoadExplanation = viewModel::loadExplanation,
                            onChoose = viewModel::choose,
                            primaryAction = { result ->
                                val canReplace = request.canReplace && !result.unchanged
                                if (canReplace) {
                                    Button(onClick = { replaceWith(result.text) }) {
                                        Text(stringResource(R.string.replace))
                                    }
                                }
                                canReplace
                            },
                        )
                    }
                }
            }
        }
    }

    /** Hands the new text back to the app the selection came from, which swaps it in. */
    private fun replaceWith(text: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }

    private fun openSettings() {
        startActivity(MainActivity.settingsIntent(this))
        finish()
    }
}

/** The text another app sent, and whether the result can be written back to it. */
data class QuickRequest(val text: String, val canReplace: Boolean) {
    companion object {
        fun from(intent: Intent?): QuickRequest? {
            val (text, canReplace) = when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT ->
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() to
                        !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() to false
                else -> null to false
            }
            return text?.takeIf { it.isNotBlank() }?.let { QuickRequest(it, canReplace) }
        }
    }
}
