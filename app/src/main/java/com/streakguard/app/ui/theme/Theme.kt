package com.streakguard.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
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
 * Also forces the system status bar dark with light icons so it matches the
 * app's top bar on every API level (the XML theme covers the launch phase).
 */
@Composable
fun StreakGuardTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppBackground.toArgb()
            window.navigationBarColor = AppBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = StreakGuardColors,
        typography = StreakGuardTypography,
        content = content,
    )
}
