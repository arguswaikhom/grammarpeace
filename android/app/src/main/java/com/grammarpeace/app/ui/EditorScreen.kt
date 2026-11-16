package com.grammarpeace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.grammarpeace.app.R
import com.grammarpeace.app.core.WritingAction
import com.grammarpeace.app.ui.components.ActionRow
import com.grammarpeace.app.ui.components.ResultSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: WritingUiState,
    snackbar: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onAction: (WritingAction) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onUseAsInput: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleChanges: () -> Unit,
    onLoadAlternatives: () -> Unit,
    onLoadExplanation: () -> Unit,
    onChoose: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.hasApiKey) SetupCard(onOpenSettings)

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("input"),
                placeholder = { Text(stringResource(R.string.input_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.large,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPaste) { Text(stringResource(R.string.paste)) }
                if (state.input.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                }
                Spacer(Modifier.weight(1f))
                val words = wordCount(state.input)
                Text(
                    text = pluralStringResource(R.plurals.word_count, words, words),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionRow(
                enabled = state.input.isNotBlank(),
                translateTo = state.translateTo,
                onAction = onAction,
            )

            ResultSection(
                state = state,
                onCopy = onCopy,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
                onToggleChanges = onToggleChanges,
                onLoadAlternatives = onLoadAlternatives,
                onLoadExplanation = onLoadExplanation,
                onChoose = onChoose,
                secondaryActions = { result ->
                    TextButton(onClick = { onShare(result.text) }) { Text(stringResource(R.string.share)) }
                    if (!result.unchanged) {
                        TextButton(onClick = { onUseAsInput(result.text) }) {
                            Text(stringResource(R.string.use_as_input))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
        }
    }
}

private val whitespace = Regex("\\s+")

private fun wordCount(text: String): Int = text.trim().let { if (it.isEmpty()) 0 else it.split(whitespace).size }
