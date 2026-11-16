package com.grammarpeace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// GrammarPeace is black and white on every platform, colour is kept for the diff.
private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF404040),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF404040),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDEDED),
    onTertiaryContainer = Color(0xFF171717),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF525252),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFD4D4D4),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Color(0xFF262626),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color.White,
    surfaceTint = Color(0xFF111111),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF404040),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFD4D4D4),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFD4D4D4),
    onTertiary = Color(0xFF171717),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF404040),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF171717),
    inversePrimary = Color(0xFF111111),
    surfaceTint = Color(0xFFFAFAFA),
)

/** Colours for highlighting what the AI changed. */
@Immutable
data class DiffColors(
    val addedBackground: Color,
    val addedText: Color,
    val removedBackground: Color,
    val removedText: Color,
    val success: Color,
)

private val LightDiff = DiffColors(
    addedBackground = Color(0xFFDCFCE7),
    addedText = Color(0xFF14532D),
    removedBackground = Color(0xFFFEE2E2),
    removedText = Color(0xFF991B1B),
    success = Color(0xFF15803D),
)

private val DarkDiff = DiffColors(
    addedBackground = Color(0xFF14532D),
    addedText = Color(0xFFDCFCE7),
    removedBackground = Color(0xFF7F1D1D),
    removedText = Color(0xFFFECACA),
    success = Color(0xFF4ADE80),
)

val LocalDiffColors = staticCompositionLocalOf { LightDiff }

@Composable
fun GrammarPeaceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDiffColors provides if (darkTheme) DarkDiff else LightDiff) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
