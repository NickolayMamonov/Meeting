package dev.whysoezzy.meetings.presentation

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.whysoezzy.features_meetings.R
import dev.whysoezzy.meetings.mappers.toEventCardTags
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.search.UIKitSearchBar
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.error.asUserMessage
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import org.koin.androidx.compose.koinViewModel
import dev.whysoezzy.uikit.R as UIKitR

private const val AD_BLOCK_INTERVAL = 3

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = koinViewModel(),
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onUserProfileClick: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is MainScreenNavEvent.NavigateToCommunity -> onCommunityClick(event.communityId)
                    is MainScreenNavEvent.NavigateToMeeting -> onMeetingClick(event.meetingId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            MainScreenTopBar(
                searchQuery = (uiState as? MainScreenUiState.Success)?.searchQuery ?: "",
                onSearchQueryChange = { query ->
                    viewModel.onEvent(MainScreenEvent.Search(query))
                },
                onProfileClick = onProfileClick,
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is MainScreenUiState.Loading -> {
                    LoadingContent()
                }

                is MainScreenUiState.Success -> {
                    val pagedMeetings = viewModel.pagedMeetings.collectAsLazyPagingItems()
                    MainScreenContent(
                        heroMeetings = state.heroMeetings,
                        popularMeetings = state.popularMeetings,
                        searchResults = state.allMeetings,
                        searchQuery = state.searchQuery,
                        pagedMeetings = pagedMeetings,
                        communities = state.communities,
                        adBlocks = state.adBlocks,
                        onMeetingClick = onMeetingClick,
                        onCommunityClick = onCommunityClick,
                        onUserProfileClick = onUserProfileClick,
                        onCommunitySubscribeClick = { communityId, isSubscribed ->
                            viewModel.onEvent(
                                MainScreenEvent.CommunitySubscriptionChanged(communityId, isSubscribed),
                            )
                        },
                    )
                }

                is MainScreenUiState.Error -> {
                    ErrorContent(
                        message = state.errorType.asUserMessage(),
                        onRetry = {
                            viewModel.onEvent(MainScreenEvent.Retry)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenTopBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProfileClick: () -> Unit,
) {
    UIKitSearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        placeholder = stringResource(R.string.meetings_main_search_placeholder),
        onProfileClick = onProfileClick,
        onCancelClick = {},
        modifier = modifier,
    )
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainScreenContent(
    heroMeetings: List<UIKitMeetingInfo>,
    popularMeetings: List<UIKitMeetingInfo>,
    searchResults: List<UIKitMeetingInfo>,
    searchQuery: String,
    pagedMeetings: LazyPagingItems<UIKitMeetingInfo>,
    communities: List<UIKitCommunityInfo>,
    adBlocks: List<UIKitAdBlock>,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit,
    onCommunitySubscribeClick: (Long, Boolean) -> Unit,
) {
    // Генерируем бесконечный циклический список рекламных блоков, чтобы типы чередовались
    val isSearching = searchQuery.isNotBlank()
    val cyclingAdBlocks = rememberCyclingAdBlocks(adBlocks)
    val searchWithAds = remember(searchResults, cyclingAdBlocks) {
        buildMeetingsWithAdsList(searchResults, cyclingAdBlocks)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // Блок Hero-встреч: широкие карточки вверху
        if (heroMeetings.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(heroMeetings, key = { it.id }) { meeting ->
                        val eventCardTags = remember(meeting.tags) { meeting.tags.toEventCardTags() }
                        UIKitEventCard(
                            imageUrl = meeting.imageUrl,
                            title = meeting.title,
                            date = meeting.date,
                            address = UIKitAddress(
                                address = meeting.address,
                                latitude = 0.0,
                                longitude = 0.0,
                            ),
                            tags = eventCardTags,
                            cardType = UIKitEventCardType.WIDE,
                            onCardClick = { onMeetingClick(meeting.id) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Блок Популярных встреч: компактные карточки
        if (popularMeetings.isNotEmpty()) {
            item {
                TextHeading2(
                    text = stringResource(R.string.meetings_main_section_upcoming),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(popularMeetings, key = { it.id }) { meeting ->
                        val eventCardTags = remember(meeting.tags) { meeting.tags.toEventCardTags() }
                        UIKitEventCard(
                            imageUrl = meeting.imageUrl,
                            title = meeting.title,
                            date = meeting.date,
                            address = UIKitAddress(
                                address = meeting.address,
                                latitude = 0.0,
                                longitude = 0.0,
                            ),
                            tags = eventCardTags,
                            cardType = UIKitEventCardType.COMPACT,
                            onCardClick = { onMeetingClick(meeting.id) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Блок Рекомендуемых сообществ
        if (communities.isNotEmpty()) {
            item {
                TextHeading2(
                    text = stringResource(R.string.meetings_main_section_communities),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(communities, key = { it.id }) { community ->
                        UIKitCommunityCard(
                            imageUrl = community.imageUrl,
                            title = community.title,
                            isSubscribed = community.isSubscribed,
                            onSubscribeClick = { isSubscribed ->
                                onCommunitySubscribeClick(community.id, isSubscribed)
                            },
                            onCardClick = {
                                onCommunityClick(community.id)
                            },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция "Все встречи" с рекламой через каждые 3 встречи
        item {
            TextHeading2(
                text = stringResource(R.string.meetings_main_section_all),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        allMeetingsSection(
            isSearching = isSearching,
            searchResults = searchResults,
            searchWithAds = searchWithAds,
            pagedMeetings = pagedMeetings,
            cyclingAdBlocks = cyclingAdBlocks,
            onMeetingClick = onMeetingClick,
            onCommunityClick = onCommunityClick,
            onUserProfileClick = onUserProfileClick,
            onCommunitySubscribeClick = onCommunitySubscribeClick,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onRetry) {
                Text(stringResource(UIKitR.string.action_retry))
            }
        }
    }
}

private sealed interface MeetingOrAd {
    data class Meeting(
        val meeting: UIKitMeetingInfo,
    ) : MeetingOrAd

    data class Ad(
        val adBlock: UIKitAdBlock,
    ) : MeetingOrAd
}

/**
 * Генерирует циклический список рекламных блоков достаточной длины, чтобы
 * типы AdBlock чередовались (например, Communities → Text → People → Communities → ...).
 * Если isNotEmpty(), генерируется список из ~3×meetings.size/3 элементов.
 */
@Composable
private fun rememberCyclingAdBlocks(adBlocks: List<UIKitAdBlock>): List<UIKitAdBlock> {
    return remember(adBlocks) {
        if (adBlocks.isEmpty()) return@remember emptyList()
        // Нам нужно больше блоков, чем есть уникальных — циклически повторяем
        // Достаточно 30 рекламных слотов (хватит даже для 90+ встреч)
        List(30) { i -> adBlocks[i % adBlocks.size] }
    }
}

/**
 * Вставляет рекламный блок после каждой 3-й встречи.
 * Циклический adBlocks гарантирует чередование типов.
 */
private fun buildMeetingsWithAdsList(
    meetings: List<UIKitMeetingInfo>,
    adBlocks: List<UIKitAdBlock>,
): List<MeetingOrAd> {
    if (adBlocks.isEmpty()) return meetings.map { MeetingOrAd.Meeting(it) }

    val result = mutableListOf<MeetingOrAd>()
    var adIndex = 0

    meetings.forEachIndexed { index, meeting ->
        result.add(MeetingOrAd.Meeting(meeting))
        // После каждой 3-й встречи вставляем рекламу
        if ((index + 1) % AD_BLOCK_INTERVAL == 0 && adIndex < adBlocks.size) {
            result.add(MeetingOrAd.Ad(adBlocks[adIndex]))
            adIndex++
        }
    }

    return result
}

@Composable
private fun MeetingCardItem(
    meeting: UIKitMeetingInfo,
    onMeetingClick: (Long) -> Unit,
) {
    val eventCardTags = remember(meeting.tags) { meeting.tags.toEventCardTags() }
    UIKitEventCard(
        imageUrl = meeting.imageUrl,
        title = meeting.title,
        date = meeting.date,
        address = UIKitAddress(address = meeting.address, latitude = 0.0, longitude = 0.0),
        tags = eventCardTags,
        cardType = UIKitEventCardType.WIDE,
        onCardClick = { onMeetingClick(meeting.id) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private fun LazyListScope.pagedMeetingsLoadState(
    items: LazyPagingItems<UIKitMeetingInfo>,
    onRetry: () -> Unit,
) {
    when (items.loadState.append) {
        is LoadState.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is LoadState.Error -> item {
            PagingErrorRow(onRetry = onRetry)
        }
        else -> Unit
    }
    if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
        item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PagingErrorRow(
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.meetings_main_paging_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onRetry) {
            Text(stringResource(UIKitR.string.action_retry))
        }
    }
}

@Composable
private fun EmptySearchHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.meetings_main_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.allMeetingsSection(
    isSearching: Boolean,
    searchResults: List<UIKitMeetingInfo>,
    searchWithAds: List<MeetingOrAd>,
    pagedMeetings: LazyPagingItems<UIKitMeetingInfo>,
    cyclingAdBlocks: List<UIKitAdBlock>,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit,
    onCommunitySubscribeClick: (Long, Boolean) -> Unit,
) {
    if (isSearching) {
        items(
            items = searchWithAds,
            key = { item ->
                when (item) {
                    is MeetingOrAd.Meeting -> "meeting_${item.meeting.id}"
                    is MeetingOrAd.Ad -> "ad_${item.adBlock.id}"
                }
            },
        ) { item ->
            when (item) {
                is MeetingOrAd.Meeting -> MeetingCardItem(item.meeting, onMeetingClick)
                is MeetingOrAd.Ad -> AdBlockComponent(
                    adBlock = item.adBlock,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    onUserClick = onUserProfileClick,
                    onCommunitySubscribe = onCommunitySubscribeClick,
                    onCommunityClick = onCommunityClick,
                )
            }
        }
        if (searchResults.isEmpty()) {
            item { EmptySearchHint() }
        }
    } else {
        items(
            count = pagedMeetings.itemCount,
            key = pagedMeetings.itemKey { "meeting_${it.id}" },
        ) { index ->
            val meeting = pagedMeetings[index]
            if (meeting != null) {
                MeetingCardItem(meeting, onMeetingClick)
                if ((index + 1) % AD_BLOCK_INTERVAL == 0 && cyclingAdBlocks.isNotEmpty()) {
                    val ad = cyclingAdBlocks[(index / AD_BLOCK_INTERVAL) % cyclingAdBlocks.size]
                    AdBlockComponent(
                        adBlock = ad,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        onUserClick = onUserProfileClick,
                        onCommunitySubscribe = onCommunitySubscribeClick,
                        onCommunityClick = onCommunityClick,
                    )
                }
            }
        }
        pagedMeetingsLoadState(pagedMeetings, onRetry = { pagedMeetings.retry() })
    }
}
