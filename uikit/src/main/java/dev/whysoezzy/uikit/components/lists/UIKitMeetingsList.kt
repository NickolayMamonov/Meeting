package dev.whysoezzy.uikit.components.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.domain.models.CommunityHost
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.models.MeetingAddress
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.PersonHost
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitMeetingsList(
    meetings: List<Meeting>,
    orientation: MeetingsListOrientation = MeetingsListOrientation.Vertical,
    modifier: Modifier = Modifier,
    onMeetingClick: (Meeting) -> Unit = {}
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
                Meeting(
                    id = 1,
                    imageUrl = "",
                    title = "Android Meetup",
                    description = "Обсуждаем новые возможности Jetpack Compose",
                    time = System.currentTimeMillis(),
                    date = "15 декабря",
                    address = MeetingAddress("ул. Пушкина, 10", 55.7558, 37.6176),
                    tags = listOf(
                        MeetingTag(1, "Android", TagState.DISABLED),
                        MeetingTag(2, "Compose", TagState.ACTIVE)
                    ),
                    personHost = PersonHost(1, "Иван", "Петров", "Android разработчик", ""),
                    communityHost = CommunityHost(
                        1,
                        "Android Developers",
                        "Сообщество разработчиков",
                        "",
                        emptyList()
                    ),
                    participants = emptyList(),
                    meetingStatus = MeetingStatus.ACTIVE,
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
                Meeting(
                    id = 1,
                    imageUrl = "",
                    title = "iOS Meetup",
                    description = "SwiftUI и новые фичи iOS",
                    time = System.currentTimeMillis(),
                    date = "20 декабря",
                    address = MeetingAddress("ул. Ленина, 5", 55.7558, 37.6176),
                    tags = listOf(MeetingTag(1, "iOS", TagState.ACTIVE)),
                    personHost = PersonHost(1, "Мария", "Сидорова", "iOS разработчик", ""),
                    communityHost = CommunityHost(
                        1,
                        "iOS Developers",
                        "Сообщество разработчиков",
                        "",
                        emptyList()
                    ),
                    participants = emptyList(),
                    meetingStatus = MeetingStatus.ACTIVE,
                    isUserInParticipants = false,
                    capacity = 30
                )
            ),
            orientation = MeetingsListOrientation.Horizontal
        )
    }
}
