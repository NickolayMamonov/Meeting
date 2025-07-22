package dev.whysoezzy.uikit.components.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.avatars.UIKitAvatar
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitOverlappingAvatars(
    avatarUrls: List<String>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    overlappingPercentage: Float = 0.3f, // 30% overlap
    maxVisibleAvatars: Int = 5,
    showCount: Boolean = true,
    borderColor: Color = UIKitTheme.colors.neutralWhite,
    borderWidth: Dp = 2.dp
) {
    val visibleAvatars = avatarUrls.take(maxVisibleAvatars)
    val remainingCount = (avatarUrls.size - maxVisibleAvatars).coerceAtLeast(0)
    val factor = (1 - overlappingPercentage)

    Layout(
        modifier = modifier,
        content = {
            // Render visible avatars
            visibleAvatars.forEach { url ->
                UIKitAvatar(
                    imageUrl = url,
                    size = avatarSize,
                    modifier = Modifier
                        .border(borderWidth, borderColor, CircleShape)
                        .clip(CircleShape)
                )
            }
            
            // Show count if there are more avatars
            if (showCount && remainingCount > 0) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .border(borderWidth, borderColor, CircleShape)
                        .background(UIKitTheme.colors.neutralLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$remainingCount",
                        style = TypographyTokens.Metadata3.copy(fontWeight = FontWeight.Medium),
                        color = UIKitTheme.colors.neutralBody
                    )
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            
            if (placeables.isEmpty()) {
                layout(0, 0) {}
            } else {
                val firstWidth = placeables.first().width
                val remainingWidths = placeables.drop(1).sumOf { it.width }
                val totalWidth = (firstWidth + remainingWidths * factor).toInt()
                val height = placeables.maxOf { it.height }
                
                layout(totalWidth, height) {
                    var x = 0
                    placeables.forEachIndexed { index, placeable ->
                        placeable.placeRelative(
                            x = x,
                            y = 0,
                            zIndex = (placeables.size - index).toFloat()
                        )
                        x += (placeable.width * factor).toInt()
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun UIKitOverlappingAvatarsPreview() {
    UIKitTheme {
        val sampleAvatars = listOf(
            "https://picsum.photos/100/100?random=1",
            "https://picsum.photos/100/100?random=2",
            "https://picsum.photos/100/100?random=3",
            "https://picsum.photos/100/100?random=4",
            "https://picsum.photos/100/100?random=5",
            "https://picsum.photos/100/100?random=6",
            "https://picsum.photos/100/100?random=7",
            "https://picsum.photos/100/100?random=8"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
        ) {
            Text(
                text = "Overlapping Avatars Examples",
                style = TypographyTokens.Heading2,
                color = UIKitTheme.colors.neutralBody
            )
            
            // 3 avatars
            UIKitOverlappingAvatars(
                avatarUrls = sampleAvatars.take(3),
                avatarSize = 40.dp
            )
            
            // 5 avatars
            UIKitOverlappingAvatars(
                avatarUrls = sampleAvatars.take(5),
                avatarSize = 48.dp
            )
            
            // More than max (shows count)
            UIKitOverlappingAvatars(
                avatarUrls = sampleAvatars,
                avatarSize = 40.dp,
                overlappingPercentage = 0.4f
            )
            
            // Custom styling
            UIKitOverlappingAvatars(
                avatarUrls = sampleAvatars.take(4),
                avatarSize = 56.dp,
                overlappingPercentage = 0.2f,
                borderColor = UIKitTheme.colors.brandDefault,
                borderWidth = 3.dp
            )
            
            // Without count
            UIKitOverlappingAvatars(
                avatarUrls = sampleAvatars,
                showCount = false,
                maxVisibleAvatars = 6
            )
        }
    }
}