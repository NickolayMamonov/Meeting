package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitCommunitySubscriptionAction
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с сообществами пользователя
 *
 * @param title Заголовок блока (по умолчанию "Мои сообщества")
 * @param communities Список сообществ пользователя
 * @param onCommunityClick Колбэк при клике на сообщество
 * @param subscriptionActionForCommunity Действие подписки для каждого сообщества
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitUserCommunitiesBlock(
    modifier: Modifier = Modifier,
    title: String = "Мои сообщества",
    communities: List<UIKitCommunityInfo>,
    onCommunityClick: (Long) -> Unit,
    subscriptionActionForCommunity: (UIKitCommunityInfo) -> UIKitCommunitySubscriptionAction,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        // Заголовок
        TextHeading2(text = title)

        if (communities.isEmpty()) {
            TextBody2(
                text = "Вы пока не состоите ни в одном сообществе",
                color = ColorTokens.NeutralWeak,
            )
        } else {
            // Горизонтальный список сообществ
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(horizontal = SpacingTokens.XS),
            ) {
                items(communities, key = { it.id }) { community ->
                    UIKitCommunityCard(
                        imageUrl = community.imageUrl,
                        title = community.title,
                        subscriptionAction = subscriptionActionForCommunity(community),
                        onCardClick = { onCommunityClick(community.id) },
                    )
                }
            }
        }
    }
}
