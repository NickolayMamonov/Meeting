package dev.whysoezzy.communities.details.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import dev.whysoezzy.communities.R
import dev.whysoezzy.communities.mappers.toEventCardTagsAllDisabled
import dev.whysoezzy.communities.mappers.toEventCardTagsAllSelected
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.layouts.UIKitOverlappingAvatars
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.topbar.BackShareTopBar
import dev.whysoezzy.uikit.error.asUserMessage
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

internal const val COMMUNITY_DETAILS_TOP_BAR_TAG = "community-details-top-bar"
internal const val COMMUNITY_DETAILS_VIEWPORT_TAG = "community-details-viewport"
internal const val COMMUNITY_DETAILS_HEADER_TAG = "community-details-header"
internal const val COMMUNITY_DETAILS_TERMINAL_TAG = "community-details-terminal"

@Composable
fun CommunityDetailsScreen(
    modifier: Modifier = Modifier,
    communityId: Long,
    onBackPressed: () -> Unit,
    onSubscribersClick: () -> Unit,
    onMeetingClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit = {},
    viewModel: CommunityDetailsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(communityId) {
        viewModel.onEvent(CommunityDetailsEvent.LoadCommunity(communityId))
    }

    // Подписываемся на навигационные события
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    CommunityDetailsLayout(
        uiState = uiState,
        onBackPressed = onBackPressed,
        onShareClick = { viewModel.onEvent(CommunityDetailsEvent.ShareCommunity) },
        onSubscribeClick = {
            viewModel.onEvent(CommunityDetailsEvent.ToggleSubscription)
        },
        onSubscribersClick = {
            viewModel.onEvent(CommunityDetailsEvent.NavigateToSubscribers)
        },
        onMeetingClick = { meetingId ->
            viewModel.onEvent(CommunityDetailsEvent.NavigateToMeeting(meetingId))
        },
        onRetry = {
            viewModel.onEvent(CommunityDetailsEvent.LoadCommunity(communityId))
        },
        modifier = modifier,
    )
}

@Composable
internal fun CommunityDetailsLayout(
    uiState: CommunityDetailsUiState,
    onBackPressed: () -> Unit,
    onShareClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onSubscribersClick: () -> Unit,
    onMeetingClick: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    scaffoldContentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    topBarWindowInsets: WindowInsets = WindowInsets.statusBars,
) {
    Scaffold(
        contentWindowInsets = scaffoldContentWindowInsets,
        topBar = {
            when (val state = uiState) {
                is CommunityDetailsUiState.Success -> {
                    BackShareTopBar(
                        title = state.title,
                        onBackClick = onBackPressed,
                        onShareClick = onShareClick,
                        modifier = Modifier
                            .windowInsetsPadding(topBarWindowInsets)
                            .testTag(COMMUNITY_DETAILS_TOP_BAR_TAG),
                    )
                }
                else -> {
                    BackShareTopBar(
                        title = stringResource(R.string.community_details_title),
                        onBackClick = onBackPressed,
                        onShareClick = {},
                        modifier = Modifier
                            .windowInsetsPadding(topBarWindowInsets)
                            .testTag(COMMUNITY_DETAILS_TOP_BAR_TAG),
                    )
                }
            }
        },
    ) { paddingValues ->
        val contentModifier = modifier
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
        when (val state = uiState) {
            is CommunityDetailsUiState.Loading -> {
                LoadingContent(modifier = contentModifier)
            }

            is CommunityDetailsUiState.Success -> {
                CommunityDetailsContent(
                    state = state,
                    onSubscribeClick = onSubscribeClick,
                    onSubscribersClick = onSubscribersClick,
                    onMeetingClick = onMeetingClick,
                    modifier = contentModifier,
                )
            }

            is CommunityDetailsUiState.Error -> {
                ErrorContent(
                    message = state.errorType.asUserMessage(),
                    onRetry = onRetry,
                    modifier = contentModifier,
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag(COMMUNITY_DETAILS_VIEWPORT_TAG),
        contentPadding = PaddingValues(
            start = SpacingTokens.L,
            end = SpacingTokens.L,
            bottom = SpacingTokens.L,
        ),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(COMMUNITY_DETAILS_HEADER_TAG),
            ) {
                AsyncImage(
                    model = state.imageUrl,
                    contentDescription = state.title,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(ColorTokens.NeutralLine),
                    error = ColorPainter(ColorTokens.NeutralLine),
                )
                TextHeading1(text = state.title, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
                contentPadding = PaddingValues(vertical = SpacingTokens.XS),
            ) {
                items(state.tags, key = { it.id }) { tag ->
                    UIKitTag(text = tag.text, size = UIKitTagSize.MEDIUM)
                }
            }
        }

        item {
            if (state.isSubscribed) {
                UIKitButton(
                    text = stringResource(R.string.community_details_leave),
                    onClick = onSubscribeClick,
                    state = UIKitButtonState.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                UIKitButton(
                    text = stringResource(R.string.community_details_join),
                    onClick = onSubscribeClick,
                    state = UIKitButtonState.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
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
                TextHeading2(text = stringResource(R.string.community_details_meetings))
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }

            items(state.activeMeetings, key = { it.id }) { meeting ->
                val eventCardTags = remember(meeting.tags) { meeting.tags.toEventCardTagsAllSelected() }
                UIKitEventCard(
                    imageUrl = meeting.imageUrl,
                    title = meeting.title,
                    date = meeting.date,
                    address = UIKitAddress(
                        address = meeting.address,
                        latitude = meeting.latitude,
                        longitude = meeting.longitude,
                    ),
                    tags = eventCardTags,
                    cardType = UIKitEventCardType.WIDE,
                    onCardClick = { onMeetingClick(meeting.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }
        }

        if (state.pastMeetings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.M))
                TextHeading2(text = stringResource(R.string.community_details_meetings_past))
                Spacer(modifier = Modifier.height(SpacingTokens.M))
            }

            item {
                LazyRow(
                    modifier = Modifier.testTag(COMMUNITY_DETAILS_TERMINAL_TAG),
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                ) {
                    items(state.pastMeetings, key = { it.id }) { meeting ->
                        val eventCardTags = remember(meeting.tags) { meeting.tags.toEventCardTagsAllDisabled() }
                        UIKitEventCard(
                            imageUrl = meeting.imageUrl,
                            title = meeting.title,
                            date = meeting.date,
                            address = UIKitAddress(
                                address = meeting.address,
                                latitude = meeting.latitude,
                                longitude = meeting.longitude,
                            ),
                            tags = eventCardTags,
                            cardType = UIKitEventCardType.COMPACT,
                            onCardClick = { onMeetingClick(meeting.id) },
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S),
    ) {
        TextHeading2(text = stringResource(R.string.community_details_subscribers))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSubscribersClick() },
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIKitOverlappingAvatars(
                avatarUrls = state.subscribers.map { it.avatar },
                avatarSize = 40.dp,
                maxVisibleAvatars = 5,
                showCount = true,
            )

            if (state.subscribersCount > state.subscribers.size) {
                Text(
                    text = "+${state.subscribersCount - state.subscribers.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val ErrorButtonMaxWidth = 343.dp

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            UIKitButton(
                text = stringResource(dev.whysoezzy.uikit.R.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.widthIn(max = ErrorButtonMaxWidth).fillMaxWidth(),
            )
        }
    }
}
