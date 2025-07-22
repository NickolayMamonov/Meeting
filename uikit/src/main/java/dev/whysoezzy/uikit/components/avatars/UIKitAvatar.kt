package dev.whysoezzy.uikit.components.avatars

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitAvatar(
    imageUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    placeholder: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "User Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            placeholder?.invoke() ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.neutralLine),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Default Avatar",
                    tint = colorScheme.neutralWeak,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}

@Composable
fun UIKitAvatarWithInitials(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorScheme.brandLight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = colorScheme.brandDark,
            fontSize = when {
                size <= 32.dp -> 12.sp
                size <= 48.dp -> 16.sp
                else -> 20.sp
            },
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitAvatarPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Small avatar with image
            UIKitAvatar(
                imageUrl = "https://picsum.photos/200",
                size = 40.dp
            )

            // Medium avatar without image
            UIKitAvatar(
                imageUrl = "",
                size = 64.dp
            )

            // Large avatar with image and click handler
            UIKitAvatar(
                imageUrl = "https://picsum.photos/200",
                size = 80.dp,
                onClick = { /* handle click */ }
            )

            // Avatar with initials
            UIKitAvatarWithInitials(
                initials = "AB",
                size = 56.dp
            )

            // Small avatar with initials
            UIKitAvatarWithInitials(
                initials = "John Doe",
                size = 32.dp
            )
        }
    }
}