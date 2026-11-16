package com.grammarpeace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.grammarpeace.app.R
import com.grammarpeace.app.core.AiSettings
import com.grammarpeace.app.core.Language
import com.grammarpeace.app.core.Provider
import com.grammarpeace.app.ui.theme.LocalDiffColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEdit: ((AiSettings) -> AiSettings) -> Unit,
    onTest: () -> Unit,
    onOpenLink: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val draft = state.draft
        if (draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.section_provider))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Provider.entries.forEachIndexed { index, provider ->
                    SegmentedButton(
                        selected = draft.provider == provider,
                        onClick = { onEdit { it.copy(provider = provider) } },
                        shape = SegmentedButtonDefaults.itemShape(index, Provider.entries.size),
                        icon = {},
                    ) {
                        Text(provider.shortName(), maxLines = 1)
                    }
                }
            }

            var showKey by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { key -> onEdit { it.copy(apiKeys = it.apiKeys + (it.provider to key.trim())) } },
                modifier = Modifier.fillMaxWidth().testTag("apiKey"),
                label = { Text(stringResource(R.string.api_key_label, draft.provider.shortName())) },
                placeholder = { Text(draft.provider.keyPlaceholder) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(stringResource(if (showKey) R.string.hide else R.string.show))
                    }
                },
            )

            if (draft.provider == Provider.CLAUDE) {
                PlainField(
                    label = stringResource(R.string.model),
                    value = draft.claudeModel,
                    onValueChange = { model -> onEdit { it.copy(claudeModel = model) } },
                )
            }
            if (draft.provider == Provider.OPENAI) {
                PlainField(
                    label = stringResource(R.string.base_url),
                    value = draft.openAiBaseUrl,
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { url -> onEdit { it.copy(openAiBaseUrl = url) } },
                )
                PlainField(
                    label = stringResource(R.string.model),
                    value = draft.openAiModel,
                    onValueChange = { model -> onEdit { it.copy(openAiModel = model) } },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onTest, enabled = draft.hasApiKey && state.test != TestStatus.Running) {
                    Text(stringResource(R.string.test_key))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onOpenLink(draft.provider.keyUrl) }) {
                    Text(stringResource(R.string.get_key))
                }
            }
            TestStatusLine(state.test)

            SectionTitle(stringResource(R.string.section_preferences))

            LanguagePicker(
                label = stringResource(R.string.grammar_language),
                selected = draft.grammarLanguage,
                options = Language.entries,
                onSelect = { language -> onEdit { it.copy(grammarLanguage = language) } },
            )
            LanguagePicker(
                label = stringResource(R.string.translate_to),
                selected = draft.translateTo,
                options = Language.targets,
                onSelect = { language -> onEdit { it.copy(translateTo = language) } },
            )

            SectionTitle(stringResource(R.string.section_dictionary))

            // Kept as typed while editing, parsing on every keystroke would eat the newline being typed.
            var dictionaryText by rememberSaveable { mutableStateOf(draft.customDictionary.joinToString("\n")) }
            OutlinedTextField(
                value = dictionaryText,
                onValueChange = { text ->
                    dictionaryText = text
                    val words = text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    onEdit { it.copy(customDictionary = words) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.dictionary_label)) },
                supportingText = { Text(stringResource(R.string.dictionary_help)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
            )

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PlainField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Ascii,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
        ),
    )
}

@Composable
private fun TestStatusLine(status: TestStatus) {
    val modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (status) {
        TestStatus.None -> Unit
        TestStatus.Running -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.test_running), style = MaterialTheme.typography.bodyMedium)
        }
        TestStatus.Passed -> Text(
            stringResource(R.string.test_passed),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDiffColors.current.success,
            modifier = modifier,
        )
        is TestStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: Language,
    options: List<Language>,
    onSelect: (Language) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(selected.displayName)
                Icon(
                    painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.displayName) },
                        onClick = { open = false; onSelect(language) },
                    )
                }
            }
        }
    }
}

/** "OpenRouter (free)" is too long for a third of a phone's width. */
private fun Provider.shortName(): String = when (this) {
    Provider.CLAUDE -> "Claude"
    Provider.OPENAI -> "OpenAI"
    Provider.OPENROUTER -> "OpenRouter"
}
