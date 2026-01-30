package dev.whysoezzy.meetings.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.blocks.UIKitAddressMapBlock
import dev.whysoezzy.uikit.components.blocks.UIKitCommunityBlock
import dev.whysoezzy.uikit.components.blocks.UIKitParticipantsBlock
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.cards.UIKitHostCard
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.topbar.BackShareTopBar
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunityHost
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitPersonHost
import dev.whysoezzy.uikit.models.UIKitTagState
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
import kotlin.collections.take

@Composable
fun MeetingDetailsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    onParticipantsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetingDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingDetailsEvent.LoadMeeting(meetingId))
    }

    Scaffold(
        topBar = {
            when (uiState) {
                is MeetingDetailsUiState.Success -> {
                    BackShareTopBar(
                        title = (uiState as MeetingDetailsUiState.Success).title,
                        onBackClick = onBackPressed,
                        onShareClick = { viewModel.onEvent(MeetingDetailsEvent.ShareMeeting) }
                    )
                }

                else -> {
                    BackShareTopBar(
                        title = "",
                        onBackClick = onBackPressed,
                        onShareClick = { }
                    )
                }
            }
        },
        bottomBar = {
            // Закрепленная внизу секция с кнопкой записи
            if (uiState is MeetingDetailsUiState.Success) {
                BottomActionSection(
                    totalPlaces = (uiState as MeetingDetailsUiState.Success).totalPlaces,
                    isUserJoined = (uiState as MeetingDetailsUiState.Success).isUserJoined,
                    onJoinClick = { viewModel.onEvent(MeetingDetailsEvent.JoinMeeting) },
                    onLeaveClick = { viewModel.onEvent(MeetingDetailsEvent.LeaveMeeting) }
                )
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is MeetingDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is MeetingDetailsUiState.Success -> {
                MeetingContent(
                    uiState = uiState as MeetingDetailsUiState.Success,
                    onJoinClick = { viewModel.onEvent(MeetingDetailsEvent.JoinMeeting) },
                    onLeaveClick = { viewModel.onEvent(MeetingDetailsEvent.LeaveMeeting) },
                    onHostClick = { viewModel.onEvent(MeetingDetailsEvent.NavigateToProfile(it)) },
                    onParticipantClick = {
                        viewModel.onEvent(
                            MeetingDetailsEvent.NavigateToProfile(
                                it
                            )
                        )
                    },
                    onCommunityClick = {
                        viewModel.onEvent(
                            MeetingDetailsEvent.NavigateToCommunity(
                                it
                            )
                        )
                    },
                    onOtherMeetingClick = {
                        viewModel.onEvent(
                            MeetingDetailsEvent.NavigateToMeeting(
                                it
                            )
                        )
                    },
                    onMapClick = { viewModel.onEvent(MeetingDetailsEvent.OpenMap) },
                    onParticipantsClick = onParticipantsClick,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as MeetingDetailsUiState.Error).message,
                    onRetry = { viewModel.onEvent(MeetingDetailsEvent.LoadMeeting(meetingId)) },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun BottomActionSection(
    totalPlaces: Int,
    isUserJoined: Boolean,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.L),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            // Информация о местах
            TextBody2(
                text = "Всего $totalPlaces мест. Если передумаете — отпишитесь",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Кнопка действия
            if (isUserJoined) {
                UIKitButton(
                    text = "Покинуть встречу",
                    onClick = onLeaveClick,
                    state = UIKitButtonState.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                UIKitButton(
                    text = "Записаться на встречу",
                    onClick = onJoinClick,
                    state = UIKitButtonState.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MeetingContent(
    uiState: MeetingDetailsUiState.Success,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onHostClick: (Long) -> Unit,
    onParticipantClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onOtherMeetingClick: (Long) -> Unit,
    onMapClick: () -> Unit,
    onParticipantsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S),
    ) {
        // 1. Meeting Image
        item {
            AsyncImage(
                model = uiState.imageUrl,
                contentDescription = "Изображение встречи",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
        }

        // 2. Title
        item {
            TextHeading1(
                text = uiState.title,
                textAlign = TextAlign.Start,
            )
        }

        // 3. Date and Address
        item {
            TextBody2(
                text = "${uiState.dateTime} · ${uiState.address.address}",
            )
        }

        // 4. Tags
        item {
            UIKitTagGroup(
                tags = uiState.tags.map { it.text },
                size = UIKitTagSize.MEDIUM,
            )
        }

        // 5. Description
        item {
            TextBody1(
                text = uiState.description,
            )
        }

        // 6. Host Block
        item {
            UIKitHostCard(
                title = "Ведущий",
                name = uiState.host.name,
                surname = uiState.host.surname,
                description = uiState.host.description,
                imageUrl = uiState.host.imageUrl,
                onCardClick = { onHostClick(uiState.host.id) }
            )
        }

        // 7. Address with Map - ИСПОЛЬЗОВАНИЕ НОВОГО БЛОКА ИЗ UIKIT
        item {
            UIKitAddressMapBlock(
                address = uiState.address.address,
                latitude = uiState.address.latitude,
                longitude = uiState.address.longitude,
                nearestMetro = uiState.nearestMetro,
                onMapClick = onMapClick
            )
        }

        // 8. Participants - ИСПОЛЬЗОВАНИЕ НОВОГО БЛОКА ИЗ UIKIT
        item {
            UIKitParticipantsBlock(
                participantAvatars = uiState.participants.map { it.avatar },
                participantCount = uiState.participants.size,
                onParticipantsClick = onParticipantsClick
            )
        }

        // 9. Community Block - ИСПОЛЬЗОВАНИЕ НОВОГО БЛОКА ИЗ UIKIT
        item {
            UIKitCommunityBlock(
                communityName = uiState.community.title,
                communityDescription = uiState.community.description,
                communityImageUrl = uiState.community.imageUrl,
                onCommunityClick = { onCommunityClick(uiState.community.id) }
            )
        }

        // 10. Other Meetings
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
            ) {
                TextHeading2(
                    text = "Другие встречи сообщества",
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                ) {
                    items(uiState.otherMeetings) { meeting ->
                        UIKitEventCard(
                            imageUrl = meeting.imageUrl,
                            title = meeting.title,
                            date = meeting.date,
                            address = UIKitAddress(
                                address = meeting.address,
                                latitude = 0.0,
                                longitude = 0.0
                            ),
                            tags = meeting.tags.map { tag ->
                                UIKitEventCardTag(
                                    text = tag.text,
                                    isSelected = tag.state == UIKitTagState.SELECTED,
                                    isEnabled = tag.state != UIKitTagState.DISABLED
                                )
                            },
                            cardType = UIKitEventCardType.COMPACT,
                            onCardClick = { onOtherMeetingClick(meeting.id) }
                        )
                    }
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(SpacingTokens.L))
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextBody1(
                text = message,
                textAlign = TextAlign.Center
            )

            UIKitButton(
                text = "Повторить",
                onClick = onRetry
            )

            UIKitButton(
                text = "Назад",
                onClick = onBackPressed
            )
        }
    }
}

@Preview
@Composable
private fun MeetingDetailsScreenPreview() {
    UIKitTheme {
        val mockTags = listOf(
            UIKitMeetingTag(1, "Android", UIKitTagState.ACTIVE),
            UIKitMeetingTag(2, "Kotlin", UIKitTagState.ACTIVE),
            UIKitMeetingTag(3, "Design", UIKitTagState.ACTIVE)
        )

        val mockParticipants = listOf(
            UIKitPerson(1, "Анна", "Иванова", "https://picsum.photos/100/100?random=1", "Дизайнер"),
            UIKitPerson(2, "Петр", "Петров", "https://picsum.photos/100/100?random=2", "Разработчик"),
            UIKitPerson(3, "Мария", "Сидорова", "https://picsum.photos/100/100?random=3", "PM")
        )

        val mockOtherMeetings = listOf(
            UIKitMeetingInfo(
                id = 100,
                imageUrl = "https://picsum.photos/212/148?random=100",
                title = "Мастер-класс по Jetpack Compose",
                date = "20 декабря 2024, 18:00",
                address = "ул. Пушкина, 10",
                tags = mockTags.take(2),
                meetingStatus = UIKitMeetingStatus.ACTIVE
            )
        )

        MeetingContent(
            uiState = MeetingDetailsUiState.Success(
                meetingId = 1,
                imageUrl = "https://picsum.photos/800/400",
                title = "Встреча разработчиков Android",
                dateTime = "15 декабря 2024, 19:00",
                address = UIKitAddress(
                    address = "ул. Тверская, 15, офис 301",
                    latitude = 55.7558,
                    longitude = 37.6176
                ),
                tags = mockTags,
                description = "Обсуждение новых технологий...",
                host = UIKitPersonHost(
                    id = 1,
                    name = "Александр",
                    surname = "Петров",
                    description = "Senior Android Developer",
                    imageUrl = "https://picsum.photos/200/200?random=host"
                ),
                nearestMetro = "М. Охотный ряд",
                participants = mockParticipants,
                isUserJoined = false,
                totalPlaces = 30,
                community = UIKitCommunityHost(
                    id = 1,
                    title = "Android Developers Moscow",
                    description = "Сообщество разработчиков...",
                    imageUrl = "https://picsum.photos/300/300?random=community",
                    meetingsInfo = mockOtherMeetings
                ),
                otherMeetings = mockOtherMeetings
            ),
            onJoinClick = { },
            onLeaveClick = { },
            onHostClick = { },
            onParticipantClick = { },
            onCommunityClick = { },
            onOtherMeetingClick = { },
            onMapClick = { },
            onParticipantsClick = { }
        )
    }
}
