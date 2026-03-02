package dev.whysoezzy.meetings.details.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun MeetingDetailsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    onParticipantsClick: () -> Unit,
    onCommunityClick: (Long) -> Unit,
    onHostClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit = {},
    onOtherMeetingClick: (Long) -> Unit = {},
    viewModel: MeetingDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingDetailsEvent.LoadMeeting(meetingId))
    }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MeetingDetailsNavEvent.NavigateToProfile -> onUserProfileClick(event.userId)
                is MeetingDetailsNavEvent.NavigateToMeeting -> onOtherMeetingClick(event.meetingId)
                is MeetingDetailsNavEvent.NavigateToCommunity -> onCommunityClick(event.communityId)
                is MeetingDetailsNavEvent.OpenMap ->
                    openMapIntent(context, event.latitude, event.longitude, event.address)
                is MeetingDetailsNavEvent.ShareMeeting ->
                    shareIntent(context, event.title, event.shareText)
            }
        }
    }

    val successState = uiState as? MeetingDetailsUiState.Success

    Scaffold(
        topBar = {
            BackShareTopBar(
                title = successState?.title ?: "",
                onBackClick = onBackPressed,
                onShareClick = {
                    if (successState != null) viewModel.onEvent(MeetingDetailsEvent.ShareMeeting)
                }
            )
        },
        bottomBar = {
            if (successState != null) {
                BottomActionSection(
                    totalPlaces = successState.totalPlaces,
                    isUserJoined = successState.isUserJoined,
                    onJoinClick = { viewModel.onEvent(MeetingDetailsEvent.JoinMeeting) },
                    onLeaveClick = { viewModel.onEvent(MeetingDetailsEvent.LeaveMeeting) }
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is MeetingDetailsUiState.Loading -> {
                LoadingContent(
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingDetailsUiState.Success -> {
                MeetingContent(
                    uiState = state,
                    onHostClick = onHostClick,
                    onParticipantClick = { userId ->
                        viewModel.onEvent(MeetingDetailsEvent.NavigateToProfile(userId))
                    },
                    onCommunityClick = onCommunityClick,
                    onOtherMeetingClick = { meetId ->
                        viewModel.onEvent(MeetingDetailsEvent.NavigateToMeeting(meetId))
                    },
                    onMapClick = { viewModel.onEvent(MeetingDetailsEvent.OpenMap) },
                    onParticipantsClick = onParticipantsClick,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingDetailsUiState.Error -> {
                ErrorContent(
                    message = state.message,
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
            TextBody2(
                text = "Всего $totalPlaces мест. Если передумаете — отпишитесь",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (isUserJoined) {
                UIKitButton(
                    text = "Покинуть встречу",
                    onClick = onLeaveClick,
                    state = UIKitButtonState.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                UIKitButton(
                    text = "Записаться на встречу",
                    onClick = onJoinClick,
                    state = UIKitButtonState.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MeetingContent(
    uiState: MeetingDetailsUiState.Success,
    onHostClick: (Long) -> Unit,
    onParticipantClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onOtherMeetingClick: (Long) -> Unit,
    onMapClick: () -> Unit,
    onParticipantsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        item {
            AsyncImage(
                model = uiState.imageUrl,
                contentDescription = "Изображение встречи",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        item {
            TextHeading1(text = uiState.title, textAlign = TextAlign.Start)
        }

        item {
            TextBody2(text = "${uiState.dateTime} · ${uiState.address.address}")
        }

        item {
            UIKitTagGroup(tags = uiState.tags.map { it.text }, size = UIKitTagSize.MEDIUM)
        }

        item {
            TextBody1(text = uiState.description)
        }

        uiState.host?.let { host ->
            item {
                UIKitHostCard(
                    title = "Ведущий",
                    name = host.name,
                    surname = host.surname,
                    description = host.description,
                    imageUrl = host.imageUrl,
                    onCardClick = { onHostClick(host.id) }
                )
            }
        }

        item {
            UIKitAddressMapBlock(
                address = uiState.address.address,
                latitude = uiState.address.latitude,
                longitude = uiState.address.longitude,
                nearestMetro = uiState.nearestMetro,
                onMapClick = onMapClick
            )
        }

        item {
            UIKitParticipantsBlock(
                participantAvatars = uiState.participants.map { it.avatar },
                participantCount = uiState.participants.size,
                onParticipantsClick = onParticipantsClick
            )
        }

        uiState.community?.let { community ->
            item {
                UIKitCommunityBlock(
                    communityName = community.title,
                    communityDescription = community.description,
                    communityImageUrl = community.imageUrl,
                    onCommunityClick = { onCommunityClick(community.id) }
                )
            }
        }

        if (uiState.otherMeetings.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)) {
                    TextHeading2(text = "Другие встречи сообщества")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)) {
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
        }

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
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextBody1(text = message, textAlign = TextAlign.Center)
            UIKitButton(text = "Повторить", onClick = onRetry)
            UIKitButton(text = "Назад", onClick = onBackPressed)
        }
    }
}

private fun openMapIntent(context: Context, lat: Double, lng: Double, address: String) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address)})")
    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng")))
    }
}

private fun shareIntent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться встречей"))
}

@Preview
@Composable
private fun MeetingDetailsScreenPreview() {
    UIKitTheme {
        val mockTags = listOf(
            UIKitMeetingTag(1, "Android", UIKitTagState.ACTIVE),
            UIKitMeetingTag(2, "Kotlin", UIKitTagState.ACTIVE)
        )
        val mockOtherMeetings = listOf(
            UIKitMeetingInfo(
                id = 100,
                imageUrl = "https://picsum.photos/212/148?random=100",
                title = "Мастер-класс по Compose",
                date = "20 декабря 2024, 18:00",
                address = "ул. Пушкина, 10",
                tags = mockTags,
                meetingStatus = UIKitMeetingStatus.ACTIVE
            )
        )
        MeetingContent(
            uiState = MeetingDetailsUiState.Success(
                meetingId = 1,
                imageUrl = "https://picsum.photos/800/400",
                title = "Встреча разработчиков Android",
                dateTime = "15 декабря 2024, 19:00",
                address = UIKitAddress("ул. Тверская, 15", 55.7558, 37.6176),
                tags = mockTags,
                description = "Обсуждение новых технологий Android",
                host = UIKitPersonHost(1, "Александр", "Петров", "Senior Android Developer",
                    "https://picsum.photos/200/200?random=host"),
                nearestMetro = "Охотный ряд",
                participants = listOf(
                    UIKitPerson(1, "Анна", "Иванова", "https://picsum.photos/100/100?random=1", "Дизайнер")
                ),
                isUserJoined = false,
                totalPlaces = 30,
                community = UIKitCommunityHost(
                    id = 1,
                    title = "Android Developers Moscow",
                    description = "Сообщество разработчиков",
                    imageUrl = "https://picsum.photos/300/300?random=community",
                    meetingsInfo = mockOtherMeetings
                ),
                otherMeetings = mockOtherMeetings
            ),
            onHostClick = {},
            onParticipantClick = {},
            onCommunityClick = {},
            onOtherMeetingClick = {},
            onMapClick = {},
            onParticipantsClick = {}
        )
    }
}
