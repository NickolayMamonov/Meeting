package dev.whysoezzy.uikit.tokens

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for colors
 * These tokens define all the colors used throughout the app
 */
object ColorTokens {
    // Brand Colors
    val BrandDark = Color(0xFF660EC8)
    val BrandDefault = Color(0xFF9A41FE)
    val BrandDarkMode = Color(0xFF8207E8)
    val BrandLight = Color(0xFFECDAFF)
    val BrandBackground = Color(0xFFF5ECFF)
    
    // Neutral Colors
    val NeutralActive = Color(0xFF29183B)
    val NeutralDark = Color(0xFF190E26)
    val NeutralBody = Color(0xFF1D0835)
    val NeutralWeak = Color(0xFFA4A4A4)
    val NeutralDisabled = Color(0xFFADB5BD)
    val NeutralLine = Color(0xFFEDEDED)
    val NeutralSecondaryBackground = Color(0xFFF7F7FC)
    val NeutralWhite = Color(0xFFFFFFFF)
    
    // Accent Colors
    val AccentDanger = Color(0xFFE94242)
    val AccentWarning = Color(0xFFFDCF41)
    val AccentSuccess = Color(0xFF2CC069)
    val AccentSafe = Color(0xFF7BCBCF)
    
    // Gradient Colors
    val PrimaryGradientColors = listOf(
        Color(0xFFED3CCA), Color(0xFFDF34D2), Color(0xFFD02BD9), Color(0xFFBF22E1),
        Color(0xFFAE1AE8), Color(0xFF9A10F0), Color(0xFF8306F7), Color(0xFF6600FF)
    )
    
    val SecondaryGradientColors = listOf(
        Color(0xFFFEF1FB), Color(0xFFFDF1FC), Color(0xFFFCF0FC), Color(0xFFFBF0FD),
        Color(0xFFF9EFFD), Color(0xFFF8EEFE), Color(0xFFF6EEFE), Color(0xFFF4EDFF)
    )
}