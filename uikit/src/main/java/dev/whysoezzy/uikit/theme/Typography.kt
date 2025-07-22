package dev.whysoezzy.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Immutable
data class UIKitTypography(
    val heading1: TextStyle,
    val heading2: TextStyle,
    val subheading1: TextStyle,
    val subheading2: TextStyle,
    val bodyText1: TextStyle,
    val bodyText2: TextStyle,
    val metadata1: TextStyle,
    val metadata2: TextStyle,
    val metadata3: TextStyle,
)

val DefaultTypography = UIKitTypography(
    heading1 = TypographyTokens.Heading1,
    heading2 = TypographyTokens.Heading2,
    subheading1 = TypographyTokens.Subheading1,
    subheading2 = TypographyTokens.Subheading2,
    bodyText1 = TypographyTokens.BodyText1,
    bodyText2 = TypographyTokens.BodyText2,
    metadata1 = TypographyTokens.Metadata1,
    metadata2 = TypographyTokens.Metadata2,
    metadata3 = TypographyTokens.Metadata3
)

val LocalUIKitTypography = staticCompositionLocalOf {
    DefaultTypography
}