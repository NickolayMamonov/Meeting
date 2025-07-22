package dev.whysoezzy.uikit.components.dividers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitDivider(
    modifier: Modifier = Modifier,
    color: Color = UIKitTheme.colors.neutralLine,
    thickness: Dp = 1.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun UIKitVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = UIKitTheme.colors.neutralLine,
    thickness: Dp = 1.dp,
    height: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .width(thickness)
            .height(height)
            .background(color)
    )
}

@Preview(showBackground = true)
@Composable
fun UIKitDividerPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextBody1(text = "Section 1")
            
            UIKitDivider()
            
            TextBody1(text = "Section 2")
            
            UIKitDivider(
                color = UIKitTheme.colors.brandDefault,
                thickness = 2.dp
            )
            
            TextBody1(text = "Section 3")
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
            ) {
                TextBody1(text = "Left")
                UIKitVerticalDivider()
                TextBody1(text = "Right")
            }
        }
    }
}