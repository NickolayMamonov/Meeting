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
import dev.whysoezzy.domain.models.SocialMedia
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitSocialMediaList(
    socialMedias: Map<SocialMedia, String>,
    modifier: Modifier = Modifier,
    onSocialMediaClick: (SocialMedia, String) -> Unit = { _, _ -> }
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        items(socialMedias.entries.toList()) { (socialMedia, url) ->
            UIKitSocialMediaItem(
                socialMedia = socialMedia,
                url = url,
                onClick = { onSocialMediaClick(socialMedia, url) }
            )
        }
    }
}

@Composable
private fun UIKitSocialMediaItem(
    socialMedia: SocialMedia,
    url: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(BorderRadiusTokens.S))
            .background(getSocialMediaColor(socialMedia).copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(
                horizontal = SpacingTokens.S,
                vertical = SpacingTokens.XS
            ),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = getSocialMediaIcon(socialMedia),
            contentDescription = getSocialMediaName(socialMedia),
            tint = getSocialMediaColor(socialMedia),
            modifier = Modifier.size(16.dp)
        )

        TextMetadata2(
            text = getSocialMediaName(socialMedia),
            color = getSocialMediaColor(socialMedia)
        )
    }
}

@Composable
private fun getSocialMediaIcon(socialMedia: SocialMedia): Painter {
    return when (socialMedia) {
        SocialMedia.TELEGRAM -> painterResource(R.drawable.telegram_logo)
        SocialMedia.HABR -> painterResource(R.drawable.habr_icon)
    }
}

private fun getSocialMediaColor(socialMedia: SocialMedia): Color {
    return when (socialMedia) {
        SocialMedia.TELEGRAM -> Color(0xFF0088CC)
        SocialMedia.HABR -> Color(0xFF77A2B6)
    }
}

private fun getSocialMediaName(socialMedia: SocialMedia): String {
    return when (socialMedia) {
        SocialMedia.TELEGRAM -> "Telegram"
        SocialMedia.HABR -> "Habr"
    }
}

@Preview
@Composable
private fun UIKitSocialMediaListPreview() {
    UIKitTheme {
        UIKitSocialMediaList(
            socialMedias = mapOf(
                SocialMedia.TELEGRAM to "@username",
                SocialMedia.HABR to "habr.com/username",
            )
        )
    }
}
