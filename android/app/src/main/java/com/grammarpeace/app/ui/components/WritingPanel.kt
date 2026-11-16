package com.grammarpeace.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grammarpeace.app.R
import com.grammarpeace.app.core.Language
import com.grammarpeace.app.core.TextDiff
import com.grammarpeace.app.core.Tone
import com.grammarpeace.app.core.WritingAction
import com.grammarpeace.app.ui.Extra
import com.grammarpeace.app.ui.ResultState
import com.grammarpeace.app.ui.WritingUiState
import com.grammarpeace.app.ui.theme.DiffColors
import com.grammarpeace.app.ui.theme.LocalDiffColors

/** Fix, Improve, Tone and Translate. Picking one runs it straight away. */
@Composable
fun ActionRow(
    enabled: Boolean,
    translateTo: Language,
    onAction: (WritingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onAction(WritingAction.Fix) }, enabled = enabled) {
            Text(stringResource(R.string.action_fix))
        }
        FilledTonalButton(onClick = { onAction(WritingAction.Rewrite(Tone.CLEAR)) }, enabled = enabled) {
            Text(stringResource(R.string.action_improve))
        }
        MenuButton(label = stringResource(R.string.action_tone), enabled = enabled) { dismiss ->
            Tone.menuTones.forEach { tone ->
                DropdownMenuItem(
                    text = { Text(tone.displayName) },
                    onClick = { dismiss(); onAction(WritingAction.Rewrite(tone)) },
                )
            }
        }
        MenuButton(label = stringResource(R.string.action_translate), enabled = enabled) { dismiss ->
            // The language used last time goes first, it is the likely pick.
            (listOf(translateTo) + Language.targets.filter { it != translateTo }).forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = { dismiss(); onAction(WritingAction.Translate(language)) },
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    enabled: Boolean,
    items: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, enabled = enabled) {
            Text(label)
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

/**
 * Shows the outcome of the last action: progress, an error, or the result with its follow-ups.
 *
 * @param primaryAction the main button when there is something better than Copy, for example Replace.
 *   Return true if a button was shown, Copy then steps back to an outlined button.
 * @param secondaryActions extra buttons placed after Copy
 */
@Composable
fun ResultSection(
    state: WritingUiState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleChanges: () -> Unit,
    onLoadAlternatives: () -> Unit,
    onLoadExplanation: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: @Composable (ResultState.Success) -> Boolean = { false },
    secondaryActions: @Composable (ResultState.Success) -> Unit = {},
) {
    AnimatedContent(
        targetState = state.result,
        contentKey = { it::class },
        label = "result",
        modifier = modifier,
    ) { result ->
        when (result) {
            ResultState.Idle -> Spacer(Modifier.fillMaxWidth())
            is ResultState.Loading -> LoadingCard(result.action)
            is ResultState.Failure -> FailureCard(result, onRetry, onOpenSettings)
            is ResultState.Success -> SuccessCard(
                result = result,
                state = state,
                onCopy = onCopy,
                onToggleChanges = onToggleChanges,
                onLoadAlternatives = onLoadAlternatives,
                onLoadExplanation = onLoadExplanation,
                onChoose = onChoose,
                primaryAction = primaryAction,
                secondaryActions = secondaryActions,
            )
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
private fun LoadingCard(action: WritingAction) {
    Card {
        Row(
            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.working, action.label()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FailureCard(result: ResultState.Failure, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.needsSetup) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                } else {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuccessCard(
    result: ResultState.Success,
    state: WritingUiState,
    onCopy: (String) -> Unit,
    onToggleChanges: () -> Unit,
    onLoadAlternatives: () -> Unit,
    onLoadExplanation: () -> Unit,
    onChoose: (String) -> Unit,
    primaryAction: @Composable (ResultState.Success) -> Boolean,
    secondaryActions: @Composable (ResultState.Success) -> Unit,
) {
    val diffColors = LocalDiffColors.current
    // A diff of a translation would mark every word as changed, which says nothing.
    val canShowChanges = !result.isTranslation && !result.unchanged

    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.action.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canShowChanges) {
                    TextButton(onClick = onToggleChanges) {
                        Text(stringResource(if (state.showChanges) R.string.hide_changes else R.string.show_changes))
                    }
                }
            }

            if (result.unchanged && result.action == WritingAction.Fix) {
                Text(
                    text = stringResource(R.string.no_errors),
                    style = MaterialTheme.typography.bodyLarge,
                    color = diffColors.success,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                val description = stringResource(R.string.result_description, result.text)
                SelectionContainer {
                    Text(
                        text = if (canShowChanges && state.showChanges) {
                            remember(result.original, result.text, diffColors) {
                                diffText(result.original, result.text, diffColors)
                            }
                        } else {
                            AnnotatedString(result.text)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        // Screen readers get the clean result, not the struck-through words mixed in.
                        modifier = Modifier.padding(vertical = 8.dp).semantics {
                            contentDescription = description
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }

            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hasPrimary = primaryAction(result)
                if (hasPrimary) {
                    OutlinedButton(onClick = { onCopy(result.text) }) { Text(stringResource(R.string.copy)) }
                } else {
                    Button(onClick = { onCopy(result.text) }) { Text(stringResource(R.string.copy)) }
                }
                secondaryActions(result)
                if (!result.isTranslation) {
                    if (state.alternatives == Extra.Hidden) {
                        TextButton(onClick = onLoadAlternatives) { Text(stringResource(R.string.alternatives)) }
                    }
                    if (state.explanation == Extra.Hidden && !result.unchanged) {
                        TextButton(onClick = onLoadExplanation) { Text(stringResource(R.string.explain)) }
                    }
                }
            }

            ExtraSection(
                title = stringResource(R.string.alternatives),
                extra = state.alternatives,
                onRetry = onLoadAlternatives,
            ) { options ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The first suggestion stays in the list so it can be picked again.
                    (listOf(result.suggestion) + options).distinct().forEach { option ->
                        ChoiceRow(text = option, selected = option == result.text, onClick = { onChoose(option) })
                    }
                }
            }

            ExtraSection(
                title = stringResource(R.string.explanation),
                extra = state.explanation,
                onRetry = onLoadExplanation,
            ) { explanation ->
                SelectionContainer {
                    Text(explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun <T> ExtraSection(
    title: String,
    extra: Extra<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    if (extra == Extra.Hidden) return
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    when (extra) {
        Extra.Hidden -> Unit
        Extra.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        is Extra.Failed -> Column {
            Text(extra.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
        is Extra.Ready -> content(extra.value)
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun WritingAction.label(): String = when (this) {
    WritingAction.Fix -> stringResource(R.string.label_fix)
    is WritingAction.Rewrite ->
        if (tone == Tone.CLEAR) stringResource(R.string.action_improve)
        else stringResource(R.string.label_tone, tone.displayName)
    is WritingAction.Translate -> stringResource(R.string.label_translate, target.displayName)
}

private fun diffText(old: String, new: String, colors: DiffColors): AnnotatedString = buildAnnotatedString {
    for (segment in TextDiff.segments(old, new)) {
        when (segment.kind) {
            TextDiff.Kind.SAME -> append(segment.text)
            TextDiff.Kind.ADDED -> withStyle(
                SpanStyle(background = colors.addedBackground, color = colors.addedText)
            ) { append(segment.text) }
            TextDiff.Kind.REMOVED -> withStyle(
                SpanStyle(
                    background = colors.removedBackground,
                    color = colors.removedText,
                    textDecoration = TextDecoration.LineThrough,
                )
            ) { append(segment.text) }
        }
    }
}
