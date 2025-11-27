package dev.whysoezzy.uikit.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import dev.whysoezzy.domain.models.SocialMediaInfo
import dev.whysoezzy.domain.models.SocialMediaType
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitSocialMediaList(
    socialMedias: List<SocialMediaInfo>,
    modifier: Modifier = Modifier,
    onSocialMediaClick: (String) -> Unit = { }
) {
    if (socialMedias.isNotEmpty()) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            items(socialMedias) { socialMedia ->
                UIKitSocialMediaItem(
                    socialMediaInfo = socialMedia,
                    onClick = { onSocialMediaClick(socialMedia.url) }
                )
            }
        }
    }
}

@Composable
private fun UIKitSocialMediaItem(
    socialMediaInfo: SocialMediaInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(BorderRadiusTokens.S))
            .background(getSocialMediaColor(socialMediaInfo.type).copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(
                horizontal = SpacingTokens.S,
                vertical = SpacingTokens.XS
            ),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = getSocialMediaIcon(socialMediaInfo.type),
            contentDescription = getSocialMediaName(socialMediaInfo.type),
            tint = getSocialMediaColor(socialMediaInfo.type),
            modifier = Modifier.size(16.dp)
        )

        TextMetadata2(
            text = socialMediaInfo.username,
            color = getSocialMediaColor(socialMediaInfo.type)
        )
    }
}

@Composable
private fun getSocialMediaIcon(socialMediaType: SocialMediaType): Painter {
    return when (socialMediaType) {
        SocialMediaType.TELEGRAM -> painterResource(R.drawable.telegram_logo)
        SocialMediaType.HABR -> painterResource(R.drawable.habr_icon)
        else -> painterResource(R.drawable.telegram_logo) // fallback
    }
}

private fun getSocialMediaColor(socialMediaType: SocialMediaType): Color {
    return when (socialMediaType) {
        SocialMediaType.TELEGRAM -> Color(0xFF0088CC)
        SocialMediaType.HABR -> Color(0xFF77A2B6)
        SocialMediaType.GITHUB -> Color(0xFF333333)
        SocialMediaType.LINKEDIN -> Color(0xFF0077B5)
        SocialMediaType.TWITTER -> Color(0xFF1DA1F2)
        SocialMediaType.INSTAGRAM -> Color(0xFFE4405F)
    }
}

private fun getSocialMediaName(socialMediaType: SocialMediaType): String {
    return when (socialMediaType) {
        SocialMediaType.TELEGRAM -> "Telegram"
        SocialMediaType.HABR -> "Habr"
        SocialMediaType.GITHUB -> "GitHub"
        SocialMediaType.LINKEDIN -> "LinkedIn"
        SocialMediaType.TWITTER -> "Twitter"
        SocialMediaType.INSTAGRAM -> "Instagram"
    }
}

@Preview
@Composable
private fun UIKitSocialMediaListPreview() {
    UIKitTheme {
        UIKitSocialMediaList(
            socialMedias = listOf(
                SocialMediaInfo(
                    type = SocialMediaType.TELEGRAM,
                    url = "https://t.me/username",
                    username = "@username"
                ),
                SocialMediaInfo(
                    type = SocialMediaType.HABR,
                    url = "https://habr.com/users/username",
                    username = "username"
                ),
                SocialMediaInfo(
                    type = SocialMediaType.GITHUB,
                    url = "https://github.com/username",
                    username = "username"
                )
            )
        )
    }
}
