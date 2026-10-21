package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.whysoezzy.uikit.components.buttons.UIKitSubscribeButton
import dev.whysoezzy.uikit.models.UIKitCommunitySubscriptionAction
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitCommunityCard(
    imageUrl: String,
    title: String,
    subscriptionAction: UIKitCommunitySubscriptionAction,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
) {
    val colorScheme = UIKitTheme.colors

    Column(
        modifier =
            modifier
                .width(104.dp)
                .clip(RoundedCornerShape(BorderRadiusTokens.L))
                .then(
                    onCardClick?.let {
                        Modifier.clickable { it() }
                    } ?: Modifier,
                ),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.XS),
    ) {
        // Community Image
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(BorderRadiusTokens.L)),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(ColorTokens.NeutralLine),
            error = ColorPainter(ColorTokens.NeutralLine),
        )

        // Community Title
        Text(
            text = title,
            style = TypographyTokens.BodyText1,
            color = colorScheme.neutralBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        when (val action = subscriptionAction) {
            UIKitCommunitySubscriptionAction.ReadOnly -> Unit
            is UIKitCommunitySubscriptionAction.Actionable -> {
                UIKitSubscribeButton(
                    selected = action.isSubscribed,
                    onSelectedChange = action.onSubscribeClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitCommunityCardPreview() {
    UIKitTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
        ) {
            // Short title
            UIKitCommunityCard(
                imageUrl = "https://picsum.photos/104/104",
                title = "Design",
                subscriptionAction = UIKitCommunitySubscriptionAction.ReadOnly,
            )

            // Long title
            UIKitCommunityCard(
                imageUrl = "https://picsum.photos/104/104",
                title = "Очень длинный текст сообщества, который должен обрезаться",
                subscriptionAction = UIKitCommunitySubscriptionAction.Actionable(
                    isSubscribed = true,
                    onSubscribeClick = { },
                ),
            )

            // Interactive card
            UIKitCommunityCard(
                imageUrl = "https://picsum.photos/104/104",
                title = "Android Dev",
                subscriptionAction = UIKitCommunitySubscriptionAction.Actionable(
                    isSubscribed = false,
                    onSubscribeClick = { },
                ),
                onCardClick = { /* handle card click */ },
            )
        }
    }
}
