package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с встречами пользователя
 *
 * @param title Заголовок блока (по умолчанию "Мои встречи")
 * @param meetings Список встреч пользователя
 * @param onMeetingClick Колбэк при клике на встречу
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitUserMeetingsBlock(
    title: String = stringResource(R.string.uikit_user_meetings_title),
    meetings: List<UIKitMeetingInfo>,
    onMeetingClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        // Заголовок
        TextHeading2(text = title)

        if (meetings.isEmpty()) {
            TextBody2(
                text = stringResource(R.string.uikit_user_meetings_empty),
                color = ColorTokens.NeutralWeak,
            )
        } else {
            // Горизонтальный список встреч
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(horizontal = SpacingTokens.XS),
            ) {
                items(meetings, key = { it.id }) { meeting ->
                    UIKitEventCard(
                        imageUrl = meeting.imageUrl,
                        title = meeting.title,
                        date = meeting.date,
                        address = UIKitAddress(meeting.address, 0.0, 0.0),
                        tags =
                            meeting.tags.map { tag ->
                                UIKitEventCardTag(
                                    text = tag.text,
                                    isSelected = false,
                                    isEnabled = false,
                                )
                            },
                        cardType = UIKitEventCardType.COMPACT,
                        onCardClick = { onMeetingClick(meeting.id) },
                    )
                }
            }
        }
    }
}

// @Preview
// @Composable
// private fun UIKitUserMeetingsBlockPreview() {
//    UIKitTheme {
//        val mockTags = listOf(
//            UIKitMeetingTag(1,"Android", UIKitTagState.ACTIVE),
//            UIKitMeetingTag(2, "Kotlin", UIKitTagState.ACTIVE)
//        )
//
//        val mockMeetings = listOf(
//            UIKitMeetingInfo(
//                id = 1,
//                imageUrl = "https://picsum.photos/212/148?random=1",
//                title = "Android Dev Meetup",
//                address = "ул. Тверская, 15",
//                tags = mockTags,
//                date = "date",
//            ),
//            UIKitMeetingInfo(
//                id = 2,
//                imageUrl = "https://picsum.photos/212/148?random=2",
//                title = "Kotlin Conf",
//                address = "ул. Пушкина, 10",
//                tags = mockTags.take(1),
//                date = "date",
//                ),
//            UIKitMeetingInfo(
//                id = 3,
//                imageUrl = "https://picsum.photos/212/148?random=3",
//                title = "UI/UX Workshop",
//                address = "пр. Мира, 20",
//                tags = mockTags,
//                date = "date",
//            )
//        )
//
//        UIKitUserMeetingsBlock(
//            meetings = mockMeetings,
//            onMeetingClick = { }
//        )
//    }
// }

@Preview
@Composable
private fun UIKitUserMeetingsBlockEmptyPreview() {
    UIKitTheme {
        UIKitUserMeetingsBlock(
            title = "Предстоящие встречи",
            meetings = emptyList(),
            onMeetingClick = { },
        )
    }
}
