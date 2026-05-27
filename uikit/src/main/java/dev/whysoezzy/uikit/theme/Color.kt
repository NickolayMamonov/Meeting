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
    val accentSafe: Color,
)

val LightColorScheme =
    UIKitColorScheme(
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
        accentSafe = ColorTokens.AccentSafe,
    )

val DarkColorScheme =
    UIKitColorScheme(
        // Brand — в тёмной теме используем BrandDarkMode как основной
        brandDark = ColorTokens.BrandDarkMode, // #8207E8 — чуть светлее для тёмного фона
        brandDefault = ColorTokens.BrandDefault, // #9A41FE — основной акцент, оставляем
        brandDarkMode = ColorTokens.BrandDark, // #660EC8 — инверсия ролей
        brandLight = Color(0xFF2D1A45), // тёмный аналог светло-фиолетового
        brandBackground = Color(0xFF1E0F30), // тёмный аналог BrandBackground
        // Neutral — полная инверсия светлых тонов
        neutralActive = Color(0xFFF0EBF5), // почти белый — был почти чёрный #29183B
        neutralDark = Color(0xFFFFFFFF), // белый — был самый тёмный #190E26
        neutralBody = Color(0xFFE8E0F0), // светлый — был тёмно-фиолетовый #1D0835
        neutralWeak = Color(0xFF9E8FB0), // приглушённый лиловый — был серый #A4A4A4
        neutralDisabled = Color(0xFF6B5F7A), // тёмный disabled — был светло-серый #ADB5BD
        neutralLine = Color(0xFF2E2040), // тёмная разделительная линия — был #EDEDED
        neutralSecondaryBackground = Color(0xFF1A1025), // тёмный вторичный фон — был #F7F7FC
        neutralWhite = Color(0xFF0F0A1A), // основной фон — был белый #FFFFFF
        // Accent — оставляем как есть, акцентные цвета хорошо читаются на тёмном фоне
        accentDanger = ColorTokens.AccentDanger, // #E94242
        accentWarning = ColorTokens.AccentWarning, // #FDCF41
        accentSuccess = ColorTokens.AccentSuccess, // #2CC069
        accentSafe = ColorTokens.AccentSafe, // #7BCBCF
    )

val PrimaryGradient = Brush.linearGradient(colors = ColorTokens.PrimaryGradientColors)
val SecondaryGradient = Brush.linearGradient(colors = ColorTokens.SecondaryGradientColors)

val LocalUIKitColorScheme =
    staticCompositionLocalOf {
        LightColorScheme
    }
