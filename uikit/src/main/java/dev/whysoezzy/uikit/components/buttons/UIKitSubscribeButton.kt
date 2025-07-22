package dev.whysoezzy.uikit.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.SecondaryGradient
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitSubscribeButton(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = UIKitTheme.colors

    Box(
        modifier = modifier
            .height(37.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.M))
            .then(
                if (selected) {
                    Modifier.background(colorScheme.brandDefault)
                } else {
                    Modifier.background(SecondaryGradient)
                }
            )
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = 10.dp, horizontal = SpacingTokens.S),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (selected) "Unsubscribe" else "Subscribe",
            tint = if (selected) colorScheme.neutralWhite else colorScheme.brandDefault,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitSubscribeButtonPreview() {
    UIKitTheme {
        var isSubscribed by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            // Unselected state
            UIKitSubscribeButton(
                selected = false,
                onSelectedChange = { }
            )

            // Selected state
            UIKitSubscribeButton(
                selected = true,
                onSelectedChange = { }
            )

            // Interactive example
            UIKitSubscribeButton(
                selected = isSubscribed,
                onSelectedChange = { isSubscribed = it }
            )
        }
    }
}