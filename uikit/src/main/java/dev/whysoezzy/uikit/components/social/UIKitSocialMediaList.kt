package dev.whysoezzy.uikit.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.models.UIKitSocialMedia
import dev.whysoezzy.uikit.models.UIKitSocialMediaInfo
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Горизонтальный список иконок социальных сетей.
 * По дизайну: квадратные кнопки 52×52dp с фиолетовым фоном #9A10F0, белая иконка.
 */
@Composable
fun UIKitSocialMediaList(
    socialMedias: List<UIKitSocialMediaInfo>,
    modifier: Modifier = Modifier,
    onSocialMediaClick: (String) -> Unit = {}
) {
    if (socialMedias.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        items(socialMedias) { socialMedia ->
            UIKitSocialMediaButton(
                socialMediaInfo = socialMedia,
                onClick = { onSocialMediaClick(socialMedia.url) }
            )
        }
    }
}

@Composable
private fun UIKitSocialMediaButton(
    socialMediaInfo: UIKitSocialMediaInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.M))
            .background(Color(0xFF9A10F0))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = getSocialMediaIcon(socialMediaInfo.type),
            contentDescription = getSocialMediaName(socialMediaInfo.type),
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun getSocialMediaIcon(type: UIKitSocialMedia): Painter {
    return when (type) {
        UIKitSocialMedia.TELEGRAM -> painterResource(R.drawable.telegram_logo)
        UIKitSocialMedia.HABR -> painterResource(R.drawable.habr_icon)
        UIKitSocialMedia.GITHUB -> painterResource(R.drawable.habr_icon) // fallback
        UIKitSocialMedia.LINKEDIN -> painterResource(R.drawable.telegram_logo) // fallback
    }
}

private fun getSocialMediaName(type: UIKitSocialMedia): String = when (type) {
    UIKitSocialMedia.TELEGRAM -> "Telegram"
    UIKitSocialMedia.HABR -> "Habr"
    UIKitSocialMedia.GITHUB -> "GitHub"
    UIKitSocialMedia.LINKEDIN -> "LinkedIn"
}

@Preview(showBackground = true)
@Composable
private fun UIKitSocialMediaListPreview() {
    UIKitTheme {
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            UIKitSocialMediaList(
                socialMedias = listOf(
                    UIKitSocialMediaInfo(
                        type = UIKitSocialMedia.HABR,
                        url = "https://habr.com/users/username",
                        username = "username"
                    ),
                    UIKitSocialMediaInfo(
                        type = UIKitSocialMedia.TELEGRAM,
                        url = "https://t.me/username",
                        username = "@username"
                    )
                )
            )
        }
    }
}
