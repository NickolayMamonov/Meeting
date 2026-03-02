package dev.whysoezzy.meetings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.meetings.presentation.MainScreenEvent
import dev.whysoezzy.meetings.presentation.MainScreenNavEvent
import dev.whysoezzy.meetings.presentation.MainScreenUiState
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.search.UIKitSearchBar
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitTagState
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = koinViewModel(),
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onUserProfileClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MainScreenNavEvent.NavigateToCommunity -> onCommunityClick(event.communityId)
                is MainScreenNavEvent.NavigateToMeeting -> onMeetingClick(event.meetingId)
            }
        }
    }

    Scaffold(
        topBar = {
            MainScreenTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onEvent(MainScreenEvent.Search(query))
                },
                onProfileClick = onProfileClick,
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is MainScreenUiState.Loading -> {
                    LoadingContent()
                }

                is MainScreenUiState.Success -> {
                    MainScreenContent(
                        heroMeetings = state.heroMeetings,
                        popularMeetings = state.popularMeetings,
                        allMeetings = state.allMeetings,
                        communities = state.communities,
                        adBlocks = state.adBlocks,
                        onMeetingClick = onMeetingClick,
                        onCommunityClick = onCommunityClick,
                        onUserProfileClick = onUserProfileClick,
                    )
                }

                is MainScreenUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = {
                            viewModel.onEvent(MainScreenEvent.Retry)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    UIKitSearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        placeholder = "Поиск встреч и сообществ",
        onProfileClick = onProfileClick,
        onCancelClick = {}
    )
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
private fun MainScreenContent(
    heroMeetings: List<UIKitMeetingInfo>,
    popularMeetings: List<UIKitMeetingInfo>,
    allMeetings: List<UIKitMeetingInfo>,
    communities: List<UIKitCommunityInfo>,
    adBlocks: List<AdBlock>,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onUserProfileClick: (Long) -> Unit,
) {
    // Генерируем бесконечный циклический список рекламных блоков, чтобы типы чередовались
    val cyclingAdBlocks = rememberCyclingAdBlocks(adBlocks)
    val meetingsWithAds = remember(allMeetings, cyclingAdBlocks) {
        buildMeetingsWithAdsList(allMeetings, cyclingAdBlocks)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Блок Hero-встреч: широкие карточки вверху
        if (heroMeetings.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(heroMeetings) { meeting ->
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
                    text = "Ближайшие встречи",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularMeetings) { meeting ->
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
                    text = "Рекомендуемые сообщества",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(communities) { community ->
                        UIKitCommunityCard(
                            imageUrl = community.imageUrl,
                            title = community.title,
                            isSubscribed = community.isSubscribed,
                            onSubscribeClick = community.onSubscribeClick,
                            onCardClick = community.onCardClick,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция "Все встречи" с рекламой через каждые 3 встречи
        item {
            TextHeading2(
                text = "Все встречи",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(meetingsWithAds.size) { index ->
            when (val item = meetingsWithAds[index]) {
                is MeetingOrAd.Meeting -> {
                    UIKitEventCard(
                        imageUrl = item.meeting.imageUrl,
                        title = item.meeting.title,
                        date = item.meeting.date,
                        address = UIKitAddress(
                            address = item.meeting.address,
                            latitude = 0.0,
                            longitude = 0.0
                        ),
                        tags = item.meeting.tags.map { tag ->
                            UIKitEventCardTag(
                                text = tag.text,
                                isSelected = tag.state == UIKitTagState.SELECTED,
                                isEnabled = tag.state != UIKitTagState.DISABLED
                            )
                        },
                        cardType = UIKitEventCardType.WIDE,
                        onCardClick = { onMeetingClick(item.meeting.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                is MeetingOrAd.Ad -> {
                    AdBlockComponent(
                        adBlock = item.adBlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        onUserClick = onUserProfileClick
                    )
                }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

private sealed class MeetingOrAd {
    data class Meeting(val meeting: UIKitMeetingInfo) : MeetingOrAd()
    data class Ad(val adBlock: AdBlock) : MeetingOrAd()
}

/**
 * Генерирует циклический список рекламных блоков достаточной длины, чтобы
 * типы AdBlock чередовались (например, Communities → Text → People → Communities → ...).
 * Если isNotEmpty(), генерируется список из ~3×meetings.size/3 элементов.
 */
@Composable
private fun rememberCyclingAdBlocks(adBlocks: List<AdBlock>): List<AdBlock> {
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
    adBlocks: List<AdBlock>
): List<MeetingOrAd> {
    if (adBlocks.isEmpty()) return meetings.map { MeetingOrAd.Meeting(it) }

    val result = mutableListOf<MeetingOrAd>()
    var adIndex = 0

    meetings.forEachIndexed { index, meeting ->
        result.add(MeetingOrAd.Meeting(meeting))
        // После каждой 3-й встречи вставляем рекламу
        if ((index + 1) % 3 == 0 && adIndex < adBlocks.size) {
            result.add(MeetingOrAd.Ad(adBlocks[adIndex]))
            adIndex++
        }
    }

    return result
}