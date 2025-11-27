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
import dev.whysoezzy.domain.models.MeetingAddress
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    title: String = "Мои встречи",
    meetings: List<MeetingInfo>,
    onMeetingClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Заголовок
        TextHeading2(text = title)

        if (meetings.isEmpty()) {
            // Состояние пустого списка можно добавить позже
        } else {
            // Горизонтальный список встреч
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(horizontal = SpacingTokens.XS)
            ) {
                items(meetings) { meeting ->
                    UIKitEventCard(
                        imageUrl = meeting.imageUrl,
                        title = meeting.title,
                        date = formatMeetingDate(meeting.time),
                        address = MeetingAddress(meeting.address, 0.0, 0.0),
                        tags = meeting.tags.map { tag ->
                            UIKitEventCardTag(
                                text = tag.text,
                                isSelected = false,
                                isEnabled = false
                            )
                        },
                        cardType = UIKitEventCardType.COMPACT,
                        onCardClick = { onMeetingClick(meeting.id) }
                    )
                }
            }
        }
    }
}

/**
 * Форматирует время встречи для отображения
 */
private fun formatMeetingDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

@Preview
@Composable
private fun UIKitUserMeetingsBlockPreview() {
    UIKitTheme {
        val mockTags = listOf(
            MeetingTag(1, "Android", TagState.ACTIVE),
            MeetingTag(2, "Kotlin", TagState.ACTIVE)
        )

        val mockMeetings = listOf(
            MeetingInfo(
                id = 1,
                imageUrl = "https://picsum.photos/212/148?random=1",
                title = "Android Dev Meetup",
                address = "ул. Тверская, 15",
                tags = mockTags,
                time = System.currentTimeMillis(),
                meetingStatus = MeetingStatus.ACTIVE
            ),
            MeetingInfo(
                id = 2,
                imageUrl = "https://picsum.photos/212/148?random=2",
                title = "Kotlin Conf",
                address = "ул. Пушкина, 10",
                tags = mockTags.take(1),
                time = System.currentTimeMillis() + 86400000,
                meetingStatus = MeetingStatus.ACTIVE
            ),
            MeetingInfo(
                id = 3,
                imageUrl = "https://picsum.photos/212/148?random=3",
                title = "UI/UX Workshop",
                address = "пр. Мира, 20",
                tags = listOf(MeetingTag(3, "Design", TagState.ACTIVE)),
                time = System.currentTimeMillis() + 172800000,
                meetingStatus = MeetingStatus.ACTIVE
            )
        )

        UIKitUserMeetingsBlock(
            meetings = mockMeetings,
            onMeetingClick = { }
        )
    }
}

@Preview
@Composable
private fun UIKitUserMeetingsBlockEmptyPreview() {
    UIKitTheme {
        UIKitUserMeetingsBlock(
            title = "Предстоящие встречи",
            meetings = emptyList(),
            onMeetingClick = { }
        )
    }
}
