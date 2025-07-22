package dev.whysoezzy.uikit.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.whysoezzy.uikit.R

/**
 * SF Pro Display FontFamily
 */
val SFProDisplayFontFamily = FontFamily(
    Font(R.font.sf_pro_display_black, FontWeight.Black),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavyitalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.sf_pro_display_lightitalic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_semibolditalic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.sf_pro_display_ultralightitalic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.sf_pro_display_thinitalic, FontWeight.Thin, FontStyle.Italic),
)

/**
 * Design tokens for typography
 * These tokens define all the text styles used throughout the app with SF Pro Display font
 */
object TypographyTokens {
    
    val Heading1 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    )
    
    val Heading2 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )
    
    val Subheading1 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    )
    
    val Subheading2 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    
    val BodyText1 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
    
    val BodyText2 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
    
    val Metadata1 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
    
    val Metadata2 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
    
    val Metadata3 = TextStyle(
        fontFamily = SFProDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
}