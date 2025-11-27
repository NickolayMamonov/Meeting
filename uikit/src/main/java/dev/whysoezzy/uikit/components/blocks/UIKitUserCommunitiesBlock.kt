package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.domain.models.CommunityInfo
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
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
    title: String = "Мои сообщества",
    communities: List<CommunityInfo>,
    subscribedCommunityIds: Set<Long> = emptySet(),
    onCommunityClick: (Long) -> Unit,
    onSubscribeClick: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Заголовок
        TextHeading2(text = title)

        if (communities.isEmpty()) {
            // Состояние пустого списка можно добавить позже
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

@Preview
@Composable
private fun UIKitUserCommunitiesBlockPreview() {
    UIKitTheme {
        val mockCommunities = listOf(
            CommunityInfo(
                id = 1,
                title = "Android Developers Moscow",
                description = "Сообщество разработчиков Android в Москве",
                imageUrl = "https://picsum.photos/300/300?random=1",
                membersCount = 1250,
                tags = listOf(
                    MeetingTag(1, "Android", TagState.ACTIVE),
                    MeetingTag(2, "Kotlin", TagState.ACTIVE)
                )
            ),
            CommunityInfo(
                id = 2,
                title = "Kotlin User Group",
                description = "Kotlin enthusiasts",
                imageUrl = "https://picsum.photos/300/300?random=2",
                membersCount = 890,
                tags = listOf(MeetingTag(2, "Kotlin", TagState.ACTIVE))
            ),
            CommunityInfo(
                id = 3,
                title = "UI/UX Designers",
                description = "Дизайнеры интерфейсов",
                imageUrl = "https://picsum.photos/300/300?random=3",
                membersCount = 567,
                tags = listOf(MeetingTag(3, "Design", TagState.ACTIVE))
            ),
            CommunityInfo(
                id = 4,
                title = "Data Science",
                description = "Аналитика данных",
                imageUrl = "https://picsum.photos/300/300?random=4",
                membersCount = 342,
                tags = listOf(MeetingTag(4, "Data", TagState.ACTIVE))
            )
        )

        UIKitUserCommunitiesBlock(
            communities = mockCommunities,
            subscribedCommunityIds = setOf(1, 3), // Подписан на 1-е и 3-е сообщества
            onCommunityClick = { },
            onSubscribeClick = { communityId, isSubscribed ->
                println("Community $communityId subscription changed to $isSubscribed")
            }
        )
    }
}

@Preview
@Composable
private fun UIKitUserCommunitiesBlockEmptyPreview() {
    UIKitTheme {
        UIKitUserCommunitiesBlock(
            title = "Подписки",
            communities = emptyList(),
            onCommunityClick = { },
            onSubscribeClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
private fun UIKitUserCommunitiesBlockOtherUserPreview() {
    UIKitTheme {
        val mockCommunities = listOf(
            CommunityInfo(
                id = 1,
                title = "Flutter Community",
                description = "Сообщество Flutter разработчиков",
                imageUrl = "https://picsum.photos/300/300?random=5",
                membersCount = 987,
                tags = listOf(MeetingTag(5, "Flutter", TagState.ACTIVE))
            ),
            CommunityInfo(
                id = 2,
                title = "React Native",
                description = "React Native разработчики",
                imageUrl = "https://picsum.photos/300/300?random=6",
                membersCount = 654,
                tags = listOf(MeetingTag(6, "React", TagState.ACTIVE))
            )
        )

        UIKitUserCommunitiesBlock(
            title = "Сообщества пользователя",
            communities = mockCommunities,
            subscribedCommunityIds = setOf(), // Пользователь не подписан ни на что
            onCommunityClick = { },
            onSubscribeClick = { _, _ -> }
        )
    }
}
