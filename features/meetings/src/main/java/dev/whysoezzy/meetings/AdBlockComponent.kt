package dev.whysoezzy.meetings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun AdBlockComponent(
    adBlock: AdBlock,
    modifier: Modifier = Modifier,
    onAdClick: (AdBlock) -> Unit = {},
    onCommunitySubscribe: (Long, Boolean) -> Unit = { _, _ -> }
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAdClick(adBlock) },
        colors = CardDefaults.cardColors(
            containerColor = UIKitTheme.colors.brandBackground
        ),
        shape = RoundedCornerShape(BorderRadiusTokens.L),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (adBlock) {
            is AdBlock.CommunityAd -> {
                CommunityAdContent(
                    adBlock = adBlock,
                    onCommunitySubscribe = onCommunitySubscribe
                )
            }
            is AdBlock.TextAd -> {
                TextAdContent(
                    adBlock = adBlock,
                    onAdClick = onAdClick
                )
            }
            is AdBlock.BannerAd -> {
                BannerAdContent(
                    adBlock = adBlock,
                    onAdClick = onAdClick
                )
            }
        }
    }
}

@Composable
private fun CommunityAdContent(
    adBlock: AdBlock.CommunityAd,
    onCommunitySubscribe: (Long, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(SpacingTokens.M),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(
            text = adBlock.title,
            color = UIKitTheme.colors.brandDark
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            items(adBlock.communities) { community ->
                UIKitCommunityCard(
                    imageUrl = community.imageUrl,
                    title = community.title,
                    isSubscribed = false, // TODO: Get actual subscription status
                    onSubscribeClick = { newState ->
                        onCommunitySubscribe(community.id, newState)
                    }
                )
            }
        }
    }
}

@Composable
private fun TextAdContent(
    adBlock: AdBlock.TextAd,
    onAdClick: (AdBlock) -> Unit
) {
    Column(
        modifier = Modifier.padding(SpacingTokens.M),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(
            text = adBlock.title,
            color = UIKitTheme.colors.brandDark
        )
        
        // Show image if available
        if (adBlock.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = adBlock.imageUrl,
                contentDescription = adBlock.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(BorderRadiusTokens.M)),
                contentScale = ContentScale.Crop
            )
        }
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            UIKitButton(
                text = adBlock.actionText,
                state = UIKitButtonState.SECONDARY,
                onClick = { onAdClick(adBlock) },
                modifier = Modifier.padding(top = SpacingTokens.S)
            )
        }
    }
}

@Composable
private fun BannerAdContent(
    adBlock: AdBlock.BannerAd,
    onAdClick: (AdBlock) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Image banner
        AsyncImage(
            model = adBlock.imageUrl,
            contentDescription = adBlock.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = BorderRadiusTokens.L,
                        topEnd = BorderRadiusTokens.L
                    )
                ),
            contentScale = ContentScale.Crop
        )
        
        // Content
        Column(
            modifier = Modifier.padding(
                start = SpacingTokens.M,
                end = SpacingTokens.M,
                bottom = SpacingTokens.M
            ),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextHeading2(
                text = adBlock.title,
                color = UIKitTheme.colors.brandDark
            )
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                UIKitButton(
                    text = adBlock.actionText,
                    state = UIKitButtonState.PRIMARY,
                    onClick = { onAdClick(adBlock) }
                )
            }
        }
    }
}
