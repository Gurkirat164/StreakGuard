package com.streakguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StreakGuardColors = darkColorScheme(
    primary = FigmaAccent,
    onPrimary = FigmaOnAccent,
    primaryContainer = FigmaPill,
    onPrimaryContainer = FigmaAmber,
    secondary = FigmaAmber,
    onSecondary = FigmaOnAccent,
    tertiary = FigmaGreen,
    onTertiary = FigmaOnAccent,
    background = FigmaBackground,
    onBackground = FigmaTextPrimary,
    surface = FigmaCard,
    onSurface = FigmaTextPrimary,
    surfaceVariant = FigmaCardInner,
    onSurfaceVariant = FigmaTextMuted,
    surfaceContainerLowest = FigmaBackground,
    surfaceContainerLow = FigmaCard,
    surfaceContainer = FigmaCard,
    surfaceContainerHigh = FigmaCardInner,
    surfaceContainerHighest = FigmaPill,
    outline = FigmaPill,
    outlineVariant = FigmaCardInner,
    error = FigmaDangerLight,
    onError = FigmaOnAccent,
    errorContainer = FigmaPill,
    onErrorContainer = FigmaDangerLight,
)

/**
 * Always-dark theme matching the Figma design. No dynamic color: the design
 * defines its own palette.
 */
@Composable
fun StreakGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreakGuardColors,
        content = content,
    )
}
