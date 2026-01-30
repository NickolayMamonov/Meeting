package dev.whysoezzy.uikit.components.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunityHost
import dev.whysoezzy.uikit.models.UIKitMeeting
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPersonHost
import dev.whysoezzy.uikit.models.UIKitTagState
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitMeetingsList(
    meetings: List<UIKitMeeting>,
    orientation: MeetingsListOrientation = MeetingsListOrientation.Vertical,
    modifier: Modifier = Modifier,
    onMeetingClick: (UIKitMeeting) -> Unit = {}
) {
    when (orientation) {
        MeetingsListOrientation.Vertical -> {
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(SpacingTokens.M)
            ) {
                items(meetings) { meeting ->
                    UIKitEventCard(
                        imageUrl = meeting.imageUrl,
                        title = meeting.title,
                        date = meeting.date,
                        address = meeting.address,
                        tags = meeting.tags.map {
                            UIKitEventCardTag(
                                it.text,
                                isSelected = false,
                                isEnabled = true
                            )
                        },
                        cardType = UIKitEventCardType.COMPACT,
                        modifier = modifier,
                        onCardClick = { onMeetingClick(meeting) }
                    )
                }
            }
        }

        MeetingsListOrientation.Horizontal -> {
            LazyRow(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                contentPadding = PaddingValues(horizontal = SpacingTokens.M)
            ) {
                items(meetings) { meeting ->
                    UIKitEventCard(
                        imageUrl = meeting.imageUrl,
                        title = meeting.title,
                        date = meeting.date,
                        address = meeting.address,
                        tags = meeting.tags.map {
                            UIKitEventCardTag(
                                it.text,
                                isSelected = false,
                                isEnabled = true
                            )
                        },
                        cardType = UIKitEventCardType.COMPACT,
                        modifier = modifier,
                        onCardClick = { onMeetingClick(meeting) }
                    )
                }
            }
        }
    }
}

enum class MeetingsListOrientation {
    Vertical,
    Horizontal
}

@Preview
@Composable
private fun UIKitMeetingsListVerticalPreview() {
    UIKitTheme {
        UIKitMeetingsList(
            meetings = listOf(
                UIKitMeeting(
                    id = 1,
                    imageUrl = "",
                    title = "Android Meetup",
                    description = "Обсуждаем новые возможности Jetpack Compose",
                    time = System.currentTimeMillis(),
                    date = "15 декабря",
                    address = UIKitAddress("ул. Пушкина, 10", 55.7558, 37.6176),
                    tags = listOf(
                        UIKitMeetingTag(1, "Android", UIKitTagState.DISABLED),
                        UIKitMeetingTag(2, "Compose", UIKitTagState.ACTIVE)
                    ),
                    personHost = UIKitPersonHost(1, "Иван", "Петров", "Android разработчик", ""),
                    communityHost = UIKitCommunityHost(
                        1,
                        "Android Developers",
                        "Сообщество разработчиков",
                        "",
                        emptyList()
                    ),
                    participants = emptyList(),
                    meetingStatus = UIKitMeetingStatus.ACTIVE,
                    isUserInParticipants = false,
                    capacity = 50
                )
            ),
            orientation = MeetingsListOrientation.Vertical
        )
    }
}

@Preview
@Composable
private fun UIKitMeetingsListHorizontalPreview() {
    UIKitTheme {
        UIKitMeetingsList(
            meetings = listOf(
                UIKitMeeting(
                    id = 1,
                    imageUrl = "",
                    title = "iOS Meetup",
                    description = "SwiftUI и новые фичи iOS",
                    time = System.currentTimeMillis(),
                    date = "20 декабря",
                    address = UIKitAddress("ул. Ленина, 5", 55.7558, 37.6176),
                    tags = listOf(UIKitMeetingTag(1, "iOS", UIKitTagState.ACTIVE)),
                    personHost = UIKitPersonHost(1, "Мария", "Сидорова", "iOS разработчик", ""),
                    communityHost = UIKitCommunityHost(
                        1,
                        "iOS Developers",
                        "Сообщество разработчиков",
                        "",
                        emptyList()
                    ),
                    participants = emptyList(),
                    meetingStatus = UIKitMeetingStatus.ACTIVE,
                    isUserInParticipants = false,
                    capacity = 30
                )
            ),
            orientation = MeetingsListOrientation.Horizontal
        )
    }
}
