package dev.whysoezzy.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.whysoezzy.uikit.tokens.ColorTokens

@Immutable
data class UIKitColorScheme(
    val brandDark: Color,
    val brandDefault: Color,
    val brandDarkMode: Color,
    val brandLight: Color,
    val brandBackground: Color,
    val neutralActive: Color,
    val neutralDark: Color,
    val neutralBody: Color,
    val neutralWeak: Color,
    val neutralDisabled: Color,
    val neutralLine: Color,
    val neutralSecondaryBackground: Color,
    val neutralWhite: Color,
    val accentDanger: Color,
    val accentWarning: Color,
    val accentSuccess: Color,
    val accentSafe: Color
)

val LightColorScheme = UIKitColorScheme(
    brandDark = ColorTokens.BrandDark,
    brandDefault = ColorTokens.BrandDefault,
    brandDarkMode = ColorTokens.BrandDarkMode,
    brandLight = ColorTokens.BrandLight,
    brandBackground = ColorTokens.BrandBackground,
    neutralActive = ColorTokens.NeutralActive,
    neutralDark = ColorTokens.NeutralDark,
    neutralBody = ColorTokens.NeutralBody,
    neutralWeak = ColorTokens.NeutralWeak,
    neutralDisabled = ColorTokens.NeutralDisabled,
    neutralLine = ColorTokens.NeutralLine,
    neutralSecondaryBackground = ColorTokens.NeutralSecondaryBackground,
    neutralWhite = ColorTokens.NeutralWhite,
    accentDanger = ColorTokens.AccentDanger,
    accentWarning = ColorTokens.AccentWarning,
    accentSuccess = ColorTokens.AccentSuccess,
    accentSafe = ColorTokens.AccentSafe
)

val PrimaryGradient = Brush.linearGradient(colors = ColorTokens.PrimaryGradientColors)
val SecondaryGradient = Brush.linearGradient(colors = ColorTokens.SecondaryGradientColors)

val LocalUIKitColorScheme = staticCompositionLocalOf {
    LightColorScheme
}