package dev.whysoezzy.meetings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.domain.models.Community
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.models.SearchResults
import dev.whysoezzy.meetings.presentation.MainScreenEvent
import dev.whysoezzy.meetings.presentation.MainScreenUiState
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.search.UIKitSearchBar
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = koinViewModel(),
    onEventClick: (Meeting) -> Unit = {},
    onCommunityClick: (Community) -> Unit = {},
    onAdClick: (AdBlock) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    UIKitTheme {
        Scaffold(
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Search Bar
                UIKitSearchBar(
                    placeholder = "Поиск событий и сообществ",
                    onSearch = { query ->
                        viewModel.onEvent(MainScreenEvent.Search(query))
                    },
                )

                // Content based on state
                when (uiState) {
                    is MainScreenUiState.Loading -> {
                        LoadingContent()
                    }

                    is MainScreenUiState.Success -> {
                        SuccessContent(
                            data = (uiState as MainScreenUiState.Success).data,
                            onEventClick = onEventClick,
                            onCommunityClick = onCommunityClick,
                            onCommunitySubscribe = { communityId, isSubscribed ->
                                viewModel.onEvent(
                                    MainScreenEvent.CommunitySubscriptionChanged(
                                        communityId,
                                        isSubscribed
                                    )
                                )
                            },
                            onAdClick = onAdClick
                        )
                    }

                    is MainScreenUiState.SearchResults -> {
                        SearchResultsContent(
                            results = (uiState as MainScreenUiState.SearchResults).results,
                            onEventClick = onEventClick,
                            onCommunityClick = onCommunityClick
                        )
                    }

                    is MainScreenUiState.Error -> {
                        ErrorContent(
                            message = (uiState as MainScreenUiState.Error).message,
                            onRetry = { viewModel.onEvent(MainScreenEvent.Retry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SuccessContent(
    data: dev.whysoezzy.domain.models.MainScreenData,
    onEventClick: (Meeting) -> Unit,
    onCommunityClick: (Community) -> Unit,
    onCommunitySubscribe: (Long, Boolean) -> Unit,
    onAdClick: (AdBlock) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        // Hero Events Section (Wide horizontal)
        item {
            HeroEventsSection(
                events = data.heroEvents,
                onEventClick = onEventClick
            )
        }

        // Popular Events Section (Compact horizontal)
        item {
            PopularEventsSection(
                events = data.popularEvents,
                onEventClick = onEventClick
            )
        }

        // Communities Section (Horizontal)
        item {
            CommunitiesSection(
                communities = data.recommendedCommunities,
                onCommunityClick = onCommunityClick,
                onCommunitySubscribe = onCommunitySubscribe
            )
        }

        // All Events Section (Vertical with ads)
        item {
            AllEventsSection(
                events = data.allEvents,
                adBlocks = data.adBlocks,
                onEventClick = onEventClick,
                onAdClick = onAdClick,
                onCommunitySubscribe = onCommunitySubscribe
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: SearchResults,
    onEventClick: (Meeting) -> Unit,
    onCommunityClick: (Community) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        if (results.events.isNotEmpty()) {
            item {
                TextHeading2(
                    text = "События",
                    modifier = Modifier.padding(horizontal = SpacingTokens.M)
                )
            }

            items(results.events) { event ->
                UIKitEventCard(
                    imageUrl = event.imageUrl,
                    title = event.title,
                    date = event.date,
                    address = event.address.address,
                    tags = event.tags.map { UIKitEventCardTag(it.text) },
                    cardType = UIKitEventCardType.WIDE,
                    onCardClick = { onEventClick(event) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacingTokens.M)
                )
            }
        }

        if (results.communities.isNotEmpty()) {
            item {
                TextHeading2(
                    text = "Сообщества",
                    modifier = Modifier.padding(horizontal = SpacingTokens.M)
                )
            }

            items(results.communities) { community ->
                UIKitCommunityCard(
                    imageUrl = community.imageUrl,
                    title = community.title,
                    isSubscribed = false, // TODO: Implement subscription status
                    onSubscribeClick = { /* TODO */ },
                    onCardClick = { onCommunityClick(community) },
                    modifier = Modifier.padding(horizontal = SpacingTokens.M)
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SpacingTokens.L)
            )

            dev.whysoezzy.uikit.components.buttons.UIKitButton(
                text = "Повторить",
                onClick = onRetry
            )
        }
    }
}

@Composable
private fun HeroEventsSection(
    events: List<Meeting>,
    onEventClick: (Meeting) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpacingTokens.M),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        items(events) { event ->
            UIKitEventCard(
                imageUrl = event.imageUrl,
                title = event.title,
                date = event.date,
                address = event.address.address,
                tags = event.tags.map { UIKitEventCardTag(it.text) },
                cardType = UIKitEventCardType.WIDE,
                onCardClick = { onEventClick(event) }
            )
        }
    }
}

@Composable
private fun PopularEventsSection(
    events: List<Meeting>,
    onEventClick: (Meeting) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = SpacingTokens.M),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Популярные события")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            items(events) { event ->
                UIKitEventCard(
                    imageUrl = event.imageUrl,
                    title = event.title,
                    date = event.date,
                    address = event.address.address,
                    tags = event.tags.map { UIKitEventCardTag(it.text) },
                    cardType = UIKitEventCardType.COMPACT,
                    onCardClick = { onEventClick(event) }
                )
            }
        }
    }
}

@Composable
private fun CommunitiesSection(
    communities: List<Community>,
    onCommunityClick: (Community) -> Unit,
    onCommunitySubscribe: (Long, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = SpacingTokens.M),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Рекомендуемые сообщества")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            items(communities) { community ->
                UIKitCommunityCard(
                    imageUrl = community.imageUrl,
                    title = community.title,
                    isSubscribed = false, // TODO: Implement subscription status
                    onSubscribeClick = { newState ->
                        onCommunitySubscribe(community.id, newState)
                    },
                    onCardClick = { onCommunityClick(community) }
                )
            }
        }
    }
}

@Composable
private fun AllEventsSection(
    events: List<Meeting>,
    adBlocks: List<AdBlock>,
    onEventClick: (Meeting) -> Unit,
    onAdClick: (AdBlock) -> Unit,
    onCommunitySubscribe: (Long, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = SpacingTokens.M),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Все события")

        // Mix events with ads every 3 items
        val mixedContent = mutableListOf<Any>()
        var adIndex = 0

        events.forEachIndexed { index, event ->
            mixedContent.add(event)

            // Add ad after every 3rd event
            if ((index + 1) % 3 == 0 && adIndex < adBlocks.size) {
                mixedContent.add(adBlocks[adIndex])
                adIndex++
            }
        }

        mixedContent.forEach { item ->
            when (item) {
                is Meeting -> {
                    UIKitEventCard(
                        imageUrl = item.imageUrl,
                        title = item.title,
                        date = item.date,
                        address = item.address.address,
                        tags = item.tags.map { UIKitEventCardTag(it.text) },
                        cardType = UIKitEventCardType.WIDE,
                        onCardClick = { onEventClick(item) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is AdBlock -> {
                    AdBlockComponent(
                        adBlock = item,
                        onAdClick = onAdClick,
                        onCommunitySubscribe = { communityId, isSubscribed ->
                            onCommunitySubscribe(communityId, isSubscribed)
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}