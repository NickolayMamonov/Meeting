package dev.whysoezzy.meetings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitPersonCard
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme

import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun AdBlockComponent(
    adBlock: AdBlock,
    modifier: Modifier = Modifier,
    onCommunitySubscribe: (Long, Boolean) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit = {},
    onAdClick: () -> Unit = {}
) {
    when (adBlock) {
        is AdBlock.CommunitiesAd -> CommunitiesAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onCommunitySubscribe = onCommunitySubscribe
        )

        is AdBlock.TextAd -> TextAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onClick = onAdClick
        )

        is AdBlock.PeopleAd -> PeopleAdBlock(
            adBlock = adBlock,
            modifier = modifier,
            onUserClick = onUserClick
        )
    }
}
@Composable
private fun CommunitiesAdBlock(
    adBlock: AdBlock.CommunitiesAd,
    modifier: Modifier = Modifier,
    onCommunitySubscribe: (Long, Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextHeading2(text = adBlock.title)
            if (adBlock.description.isNotEmpty()) {
                TextBody2(
                    text = adBlock.description,
                    color = UIKitTheme.colors.neutralWeak
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(adBlock.communities, key = {it.id}) { community ->
                UIKitCommunityCard(
                    imageUrl = community.imageUrl,
                    title = community.name,
                    isSubscribed = false,
                    onSubscribeClick = { isSubscribed ->
                        onCommunitySubscribe(community.id, isSubscribed)
                    },
                    onCardClick = { /* TODO: навигация к сообществу */ },
                )
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
private fun PeopleAdBlock(
    adBlock: AdBlock.PeopleAd,
    modifier: Modifier = Modifier,
    onUserClick: (Long) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextHeading2(text = adBlock.title)
            if (adBlock.description.isNotEmpty()) {
                TextBody2(
                    text = adBlock.description,
                    color = UIKitTheme.colors.neutralWeak
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(adBlock.users) { user ->
                UIKitPersonCard(
                    imageUrl = user.avatarUrl,
                    name = user.name,
                    role = user.role,
                    onCardClick = { onUserClick(user.id) },
                )
            }
        }
    }
}