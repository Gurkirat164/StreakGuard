package com.streakguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.streakguard.app.R

/** Headline + body typeface (Developer Suite). */
val GeistFontFamily = FontFamily(Font(R.font.geist))

/** Label / caption / mono typeface (Developer Suite). */
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono))

private val StreakGuardTypography: Typography
    get() {
        val base = Typography()
        return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = GeistFontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = GeistFontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = GeistFontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = GeistFontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = GeistFontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = GeistFontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = GeistFontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = GeistFontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = GeistFontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = GeistFontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = GeistFontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = GeistFontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
        )
    }

private val StreakGuardColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PillBackground,
    onPrimaryContainer = Tertiary,
    secondary = Secondary,
    onSecondary = OnPrimary,
    tertiary = Tertiary,
    onTertiary = OnPrimary,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardInnerBackground,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = AppBackground,
    surfaceContainerLow = CardBackground,
    surfaceContainer = CardBackground,
    surfaceContainerHigh = CardInnerBackground,
    surfaceContainerHighest = PillBackground,
    outline = PillBackground,
    outlineVariant = CardInnerBackground,
    error = DangerLight,
    onError = OnPrimary,
    errorContainer = PillBackground,
    onErrorContainer = DangerLight,
)

/**
 * Always-dark theme matching the Developer Suite palette. No dynamic color.
 *
 * MainActivity owns edge-to-edge and light system icons through AndroidX.
 */
@Composable
fun StreakGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreakGuardColors,
        typography = StreakGuardTypography,
        content = content,
    )
}
