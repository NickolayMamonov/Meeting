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
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с сообществами пользователя
 *
 * @param title Заголовок блока (по умолчанию "Мои сообщества")
 * @param communities Список сообществ пользователя
 * @param subscribedCommunityIds Список ID сообществ, на которые подписан пользователь
 * @param onCommunityClick Колбэк при клике на сообщество
 * @param onSubscribeClick Колбэк при изменении подписки на сообщество
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitUserCommunitiesBlock(
    modifier: Modifier = Modifier,
    title: String = "Мои сообщества",
    communities: List<UIKitCommunityInfo>,
    subscribedCommunityIds: Set<Long> = emptySet(),
    onCommunityClick: (Long) -> Unit,
    onSubscribeClick: (Long, Boolean) -> Unit = { _, _ -> }
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Заголовок
        TextHeading2(text = title)

        if (communities.isEmpty()) {
            TextBody2(
                text = "Вы пока не состоите ни в одном сообществе",
                color = ColorTokens.NeutralWeak
            )
        } else {
            // Горизонтальный список сообществ
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(horizontal = SpacingTokens.XS)
            ) {
                items(communities) { community ->
                    UIKitCommunityCard(
                        imageUrl = community.imageUrl,
                        title = community.title,
                        isSubscribed = community.id in subscribedCommunityIds,
                        onSubscribeClick = { isSubscribed ->
                            onSubscribeClick(community.id, isSubscribed)
                        },
                        onCardClick = { onCommunityClick(community.id) }
                    )
                }
            }
        }
    }
}
