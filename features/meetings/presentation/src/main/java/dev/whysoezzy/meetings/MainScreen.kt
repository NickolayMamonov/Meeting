package dev.whysoezzy.meetings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whysoezzy.meetings.presentation.MainScreenEvent
import dev.whysoezzy.meetings.presentation.MainScreenUiState
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.search.UIKitSearchBar
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
    onProfileClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainScreenTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onEvent(MainScreenEvent.Search(query))
                },
                onProfileClick = onProfileClick
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
                        categories = state.categories,
                        communities = state.communities,
                        onMeetingClick = onMeetingClick,
                        onCommunityClick = onCommunityClick
                    )
                }

                is MainScreenUiState.SearchResults -> {
                    SearchResultsContent(
                        meetings = state.meetings,
                        communities = state.communities,
                        onMeetingClick = onMeetingClick,
                        onCommunityClick = onCommunityClick
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Встречи",
                style = MaterialTheme.typography.headlineLarge
            )
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Профиль"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Используем UIKit компонент для поиска
        UIKitSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "Поиск встреч и сообществ",
            modifier = Modifier.fillMaxWidth()
        )
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
private fun MainScreenContent(
    heroMeetings: List<UIKitMeetingInfo>,
    popularMeetings: List<UIKitMeetingInfo>,
    allMeetings: List<UIKitMeetingInfo>,
    categories: List<UIKitMeetingTag>,
    communities: List<UIKitCommunityInfo>,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (heroMeetings.isNotEmpty()) {
            item {
                Text(
                    text = "Главные события",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

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
                            modifier = Modifier.width(320.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция категорий (фильтры)
        if (categories.isNotEmpty()) {
            item {
                Text(
                    text = "Категории",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = category.state == UIKitTagState.SELECTED,
                            onClick = { /* TODO: фильтрация по категории */ },
                            label = { Text(category.text) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция популярных встреч
        if (popularMeetings.isNotEmpty()) {
            item {
                Text(
                    text = "Популярные встречи",
                    style = MaterialTheme.typography.headlineSmall,
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
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция сообществ
        if (communities.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Рекомендуемые сообщества",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = { /* TODO: показать все сообщества */ }) {
                        Text("Все")
                    }
                }
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
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Секция всех встреч
        item {
            Text(
                text = "Все встречи",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(allMeetings) { meeting ->
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    meetings: List<UIKitMeetingInfo>,
    communities: List<UIKitCommunityInfo>,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Результаты поиска по сообществам
        if (communities.isNotEmpty()) {
            item {
                Text(
                    text = "Сообщества",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(communities) { community ->
                UIKitCommunityCard(
                    imageUrl = community.imageUrl,
                    title = community.title,
                    isSubscribed = community.isSubscribed,
                    onSubscribeClick = community.onSubscribeClick,
                    onCardClick = community.onCardClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Результаты поиска по встречам
        if (meetings.isNotEmpty()) {
            item {
                Text(
                    text = "Встречи",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(meetings) { meeting ->
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }

        // Если ничего не найдено
        if (meetings.isEmpty() && communities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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