package dev.whysoezzy.communities.details.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.layouts.UIKitOverlappingAvatars
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.topbar.BackShareTopBar
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun CommunityDetailsScreen(
    modifier: Modifier = Modifier,
    communityId: Long,
    onBackPressed: () -> Unit,
    onSubscribersClick: () -> Unit,
    onMeetingClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit = {},
    viewModel: CommunityDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(communityId) {
        viewModel.onEvent(CommunityDetailsEvent.LoadCommunity(communityId))
    }

    // Подписываемся на навигационные события
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is CommunityDetailsNavEvent.NavigateToMeeting ->
                    onMeetingClick(event.meetingId)
                is CommunityDetailsNavEvent.NavigateToProfile ->
                    onUserProfileClick(event.userId)
                is CommunityDetailsNavEvent.NavigateToSubscribers ->
                    onSubscribersClick()
                is CommunityDetailsNavEvent.ShareCommunity ->
                    shareCommunityIntent(context, event.title, event.shareText)
            }
        }
    }

    Scaffold(
        topBar = {
            when (val state = uiState) {
                is CommunityDetailsUiState.Success -> {
                    BackShareTopBar(
                        title = state.title,
                        onBackClick = onBackPressed,
                        onShareClick = { viewModel.onEvent(CommunityDetailsEvent.ShareCommunity) },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
                else -> {
                    BackShareTopBar(
                        title = "Сообщество",
                        onBackClick = onBackPressed,
                        onShareClick = {},
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CommunityDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is CommunityDetailsUiState.Success -> {
                CommunityDetailsContent(
                    state = state,
                    onSubscribeClick = {
                        viewModel.onEvent(CommunityDetailsEvent.ToggleSubscription)
                    },
                    onSubscribersClick = {
                        viewModel.onEvent(CommunityDetailsEvent.NavigateToSubscribers)
                    },
                    onMeetingClick = { meetingId ->
                        viewModel.onEvent(CommunityDetailsEvent.NavigateToMeeting(meetingId))
                    },
                    paddingValues = paddingValues,
                    modifier = modifier
                )
            }

            is CommunityDetailsUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = {
                        viewModel.onEvent(CommunityDetailsEvent.LoadCommunity(communityId))
                    },
                    modifier = Modifier.padding(paddingValues)
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
private fun CommunityDetailsContent(
    state: CommunityDetailsUiState.Success,
    onSubscribeClick: () -> Unit,
    onSubscribersClick: () -> Unit,
    onMeetingClick: (Long) -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SpacingTokens.L,
            end = SpacingTokens.L,
            top = paddingValues.calculateTopPadding(),
            bottom = paddingValues.calculateBottomPadding() + SpacingTokens.L
        ),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        item {
            AsyncImage(
                model = state.imageUrl,
                contentDescription = state.title,
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            TextHeading1(text = state.title, modifier = Modifier.fillMaxWidth())
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
                contentPadding = PaddingValues(vertical = SpacingTokens.XS)
            ) {
                items(state.tags) { tag ->
                    UIKitTag(text = tag.text, size = UIKitTagSize.MEDIUM)
                }
            }
        }

        item {
            if (state.isSubscribed) {
                UIKitButton(
                    text = "Покинуть сообщество",
                    onClick = onSubscribeClick,
                    state = UIKitButtonState.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                UIKitButton(
                    text = "Вступить в сообщество",
                    onClick = onSubscribeClick,
                    state = UIKitButtonState.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            TextBody2(text = state.description, modifier = Modifier.fillMaxWidth())
        }

        item {
            SubscribersSection(state = state, onSubscribersClick = onSubscribersClick)
        }

        if (state.activeMeetings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.M))
                TextHeading2(text = "Встречи")
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }

            items(state.activeMeetings) { meeting ->
                UIKitEventCard(
                    imageUrl = meeting.imageUrl,
                    title = meeting.title,
                    date = meeting.date,
                    address = UIKitAddress(
                        address = meeting.address.address,
                        latitude = meeting.address.latitude,
                        longitude = meeting.address.longitude
                    ),
                    tags = meeting.tags.map { tag ->
                        UIKitEventCardTag(text = tag.text, isSelected = true, isEnabled = false)
                    },
                    cardType = UIKitEventCardType.WIDE,
                    onCardClick = { onMeetingClick(meeting.id) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }
        }

        if (state.pastMeetings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.M))
                TextHeading2(text = "Прошлые встречи")
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)) {
                    items(state.pastMeetings) { meeting ->
                        UIKitEventCard(
                            imageUrl = meeting.imageUrl,
                            title = meeting.title,
                            date = meeting.date,
                            address = UIKitAddress(
                                address = meeting.address.address,
                                latitude = meeting.address.latitude,
                                longitude = meeting.address.longitude
                            ),
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
}

@Composable
private fun SubscribersSection(
    state: CommunityDetailsUiState.Success,
    onSubscribersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        TextHeading2(text = "Подписаны")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSubscribersClick() },
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitOverlappingAvatars(
                avatarUrls = state.subscribers.map { it.avatarUrl },
                avatarSize = 40.dp,
                maxVisibleAvatars = 5,
                showCount = true
            )

            if (state.subscribersCount > state.subscribers.size) {
                Text(
                    text = "+${state.subscribersCount - state.subscribers.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            UIKitButton(text = "Повторить", onClick = onRetry)
        }
    }
}

private fun shareCommunityIntent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться сообществом"))
}
