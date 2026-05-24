package dev.whysoezzy.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun UIKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: UIKitTypography = DefaultTypography,
    content: @Composable () -> Unit
) {
    val uiKitColors = if (darkTheme) DarkColorScheme else LightColorScheme
    val materialColorScheme = uiKitColors.toMaterial3ColorScheme(darkTheme)

    CompositionLocalProvider(
        LocalUIKitColorScheme provides uiKitColors,
        LocalUIKitTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

object UIKitTheme {
    val colors: UIKitColorScheme
        @Composable get() = LocalUIKitColorScheme.current

    val typography: UIKitTypography
        @Composable get() = LocalUIKitTypography.current
}

/**
 * Маппинг доменной палитры UIKit (brand / neutral / accent) на слоты Material 3.
 *
 * Берём lightColorScheme() / darkColorScheme() как базу с разумными дефолтами
 * для слотов, которые мы не покрываем явно (surfaceContainerHigh, scrim и т.п.),
 * и переопределяем слоты, на которые есть осмысленный аналог в UIKitColorScheme.
 *
 * Это даёт связку: любая правка UIKit-палитры автоматически отражается на
 * стандартных Material 3-компонентах (Button, Card, CircularProgressIndicator,
 * любой код, читающий MaterialTheme.colorScheme.*).
 */
private fun UIKitColorScheme.toMaterial3ColorScheme(isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brandDefault,
        onPrimary = neutralWhite,
        primaryContainer = brandLight,
        onPrimaryContainer = if (isDark) brandDarkMode else brandDark,

        secondary = brandDarkMode,
        onSecondary = neutralWhite,
        secondaryContainer = brandBackground,
        onSecondaryContainer = neutralDark,

        tertiary = accentSafe,
        onTertiary = neutralWhite,

        background = neutralWhite,
        onBackground = neutralDark,

        surface = neutralWhite,
        onSurface = neutralDark,
        surfaceVariant = neutralSecondaryBackground,
        onSurfaceVariant = neutralBody,

        error = accentDanger,
        onError = neutralWhite,
        errorContainer = accentDanger.copy(alpha = if (isDark) 0.20f else 0.12f),
        onErrorContainer = accentDanger,

        outline = neutralLine,
        outlineVariant = neutralLine.copy(alpha = 0.5f),
    )
}