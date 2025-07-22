package dev.whysoezzy.uikit.components.text

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import dev.whysoezzy.uikit.theme.UIKitTheme

@Composable
fun TextSubheading2(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,

    ) {
    Text(
        text = text,
        style = UIKitTheme.typography.subheading2,
        color = color,
        overflow = overflow,
        lineHeight = lineHeight,
        maxLines = maxLines,
        fontWeight = fontWeight,
        textAlign = textAlign,
        modifier = modifier
    )
}