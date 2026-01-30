package dev.whysoezzy.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.text.TextSubheading1

import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun AdBlockComponent(
    adBlock: AdBlock,
    modifier: Modifier = Modifier,
    onAdClick: (AdBlock) -> Unit = {}
) {
    when (adBlock) {
        is AdBlock.CommunityAd -> CommunityAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onClick = { onAdClick(adBlock) }
        )

        is AdBlock.TextAd -> TextAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onClick = { onAdClick(adBlock) }
        )

        is AdBlock.BannerAd -> BannerAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onClick = { onAdClick(adBlock) }
        )
    }
}

//@Composable
//fun AdBlockComponent(
//    adBlock: AdBlock,
//    modifier: Modifier = Modifier,
//    onAdClick: (AdBlock) -> Unit = {},
//) {
//    Card(
//        modifier = modifier
//            .fillMaxWidth()
//            .clickable { onAdClick(adBlock) },
//        colors = CardDefaults.cardColors(
//            containerColor = UIKitTheme.colors.brandBackground
//        ),
//        shape = RoundedCornerShape(BorderRadiusTokens.L),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        when (adBlock) {
//            is AdBlock.CommunityAd -> CommunityAdBlock(
//                adBlock = adBlock,
//                modifier = modifier,
//                onClick = { onAdClick(adBlock) }
//            )
//
//            is AdBlock.TextAd -> TextAdBlock(
//                adBlock = adBlock,
//                modifier = modifier,
//                onClick = { onAdClick(adBlock) }
//            )
//
//            is AdBlock.BannerAd -> BannerAdBlock(
//                adBlock = adBlock,
//                modifier = modifier,
//                onClick = { onAdClick(adBlock) }
//            )
//        }
//    }
//}

//@Composable
//private fun CommunityAdBlock(
//    adBlock: AdBlock.CommunityAd,
//    onCommunitySubscribe: (Long, Boolean) -> Unit
//) {
//    Column(
//        modifier = Modifier.padding(SpacingTokens.M),
//        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//    ) {
//        TextHeading2(
//            text = adBlock.title,
//            color = UIKitTheme.colors.brandDark
//        )
//
//        LazyRow(
//            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//        ) {
//            items(adBlock.communities) { community ->
//                UIKitCommunityCard(
//                    imageUrl = community.imageUrl,
//                    title = community.title,
//                    isSubscribed = false, // TODO: Get actual subscription status
//                    onSubscribeClick = { newState ->
//                        onCommunitySubscribe(community.id, newState)
//                    }
//                )
//            }
//        }
//    }
//}

@Composable
private fun CommunityAdBlock(
    adBlock: AdBlock.CommunityAd,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = adBlock.communityImageUrl,
                contentDescription = "Community image",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.XS)
            ) {
                TextSubheading1(
                    text = adBlock.communityName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                TextBody2(
                    text = adBlock.communityDescription,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (adBlock.subscribersCount > 0) {
                    TextBody2(
                        text = "${adBlock.subscribersCount} подписчиков",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TextAdBlock(
    adBlock: AdBlock.TextAd,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            TextHeading2(
                text = adBlock.title,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            TextBody1(
                text = adBlock.description,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            adBlock.actionText?.let { actionText ->
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun BannerAdBlock(
    adBlock: AdBlock.BannerAd,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = adBlock.backgroundColor?.let {
                Color(android.graphics.Color.parseColor(it))
            } ?: MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Фоновое изображение
            AsyncImage(
                model = adBlock.imageUrl,
                contentDescription = "Banner image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Заголовок поверх изображения
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(SpacingTokens.M),
                contentAlignment = Alignment.BottomStart
            ) {
                TextHeading2(
                    text = adBlock.title,
                    color = Color.White
                )
            }
        }
    }
}

//@Composable
//private fun TextAdContent(
//    adBlock: AdBlock.TextAd,
//    onAdClick: (AdBlock) -> Unit
//) {
//    Column(
//        modifier = Modifier.padding(SpacingTokens.M),
//        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//    ) {
//        TextHeading2(
//            text = adBlock.title,
//            color = UIKitTheme.colors.brandDark
//        )
//
//        // Show image if available
//        if (adBlock.imageUrl.isNotEmpty()) {
//            AsyncImage(
//                model = adBlock.imageUrl,
//                contentDescription = adBlock.title,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(120.dp)
//                    .clip(RoundedCornerShape(BorderRadiusTokens.M)),
//                contentScale = ContentScale.Crop
//            )
//        }
//
//        Box(
//            modifier = Modifier.fillMaxWidth(),
//            contentAlignment = Alignment.CenterStart
//        ) {
//            UIKitButton(
//                text = adBlock.actionText,
//                state = UIKitButtonState.SECONDARY,
//                onClick = { onAdClick(adBlock) },
//                modifier = Modifier.padding(top = SpacingTokens.S)
//            )
//        }
//    }
//}

//@Composable
//private fun BannerAdContent(
//    adBlock: AdBlock.BannerAd,
//    onAdClick: (AdBlock) -> Unit
//) {
//    Column(
//        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//    ) {
//        // Image banner
//        AsyncImage(
//            model = adBlock.imageUrl,
//            contentDescription = adBlock.title,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(120.dp)
//                .clip(
//                    RoundedCornerShape(
//                        topStart = BorderRadiusTokens.L,
//                        topEnd = BorderRadiusTokens.L
//                    )
//                ),
//            contentScale = ContentScale.Crop
//        )
//
//        // Content
//        Column(
//            modifier = Modifier.padding(
//                start = SpacingTokens.M,
//                end = SpacingTokens.M,
//                bottom = SpacingTokens.M
//            ),
//            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//        ) {
//            TextHeading2(
//                text = adBlock.title,
//                color = UIKitTheme.colors.brandDark
//            )
//
//            Box(
//                modifier = Modifier.fillMaxWidth(),
//                contentAlignment = Alignment.CenterStart
//            ) {
//                UIKitButton(
//                    text = adBlock.actionText,
//                    state = UIKitButtonState.PRIMARY,
//                    onClick = { onAdClick(adBlock) }
//                )
//            }
//        }
//    }
//}
