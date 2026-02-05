package dev.whysoezzy.meetings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.domain.usecase.SearchUseCase
import dev.whysoezzy.meetings.mappers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(
    private val getMainScreenDataUseCase: GetMainScreenDataUseCase,
    private val searchUseCase: SearchUseCase,
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _subscribedCommunityIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        loadMainScreenData()
    }

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.LoadData -> loadMainScreenData()
            is MainScreenEvent.Search -> performSearch(event.query)
            is MainScreenEvent.CommunitySubscriptionChanged -> {
                toggleCommunitySubscription(event.communityId, event.isSubscribed)
            }
            is MainScreenEvent.Retry -> loadMainScreenData()
        }
    }

    private fun loadMainScreenData() {
        viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading

            getMainScreenDataUseCase()
                .onSuccess { data ->
                    _uiState.value = MainScreenUiState.Success(
                        heroMeetings = data.heroMeetings.toUIKitMeetingInfos(),
                        popularMeetings = data.popularMeetings.toUIKitMeetingInfos(),
                        allMeetings = data.allMeetings.toUIKitMeetingInfos(),
                        categories = data.categories.toUIKitMeetingTags(),
                        communities = data.communities.toUIKitCommunityInfoList(
                            subscribedIds = _subscribedCommunityIds.value,
                            onSubscribeClick = { communityId, isSubscribed ->
                                toggleCommunitySubscription(communityId, isSubscribed)
                            },
                            onCardClick = { communityId ->
                                // TODO: Навигация к сообществу
                            }
                        ),
                        adBlocks = data.adBlocks
                    )
                }
                .onFailure { exception ->
                    _uiState.value = MainScreenUiState.Error(
                        exception.message ?: "Произошла ошибка при загрузке данных"
                    )
                }
        }
    }

    private fun performSearch(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            loadMainScreenData()
            return
        }

        viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading

            searchUseCase(query)
                .onSuccess { searchData ->
                    _uiState.value = MainScreenUiState.SearchResults(
                        meetings = searchData.meetings.toUIKitMeetingInfos(),
                        communities = searchData.communities.toUIKitCommunityInfoList(
                            subscribedIds = _subscribedCommunityIds.value,
                            onSubscribeClick = { communityId, isSubscribed ->
                                toggleCommunitySubscription(communityId, isSubscribed)
                            },
                            onCardClick = { communityId ->
                                // TODO: Навигация к сообществу
                            }
                        )
                    )
                }
                .onFailure { exception ->
                    _uiState.value = MainScreenUiState.Error(
                        exception.message ?: "Ошибка поиска"
                    )
                }
        }
    }

    private fun toggleCommunitySubscription(communityId: Long, isSubscribed: Boolean) {
        _subscribedCommunityIds.value = if (isSubscribed) {
            _subscribedCommunityIds.value + communityId
        } else {
            _subscribedCommunityIds.value - communityId
        }

        val currentState = _uiState.value
        if (currentState is MainScreenUiState.Success) {
            _uiState.value = currentState.copy(
                communities = currentState.communities.map { community ->
                    if (community.id == communityId) {
                        community.copy(isSubscribed = isSubscribed)
                    } else {
                        community
                    }
                }
            )
        }

       viewModelScope.launch {
            manageCommunitySubscriptionUseCase(communityId, isSubscribed)
                .onFailure { exception ->
                    _subscribedCommunityIds.value = if (isSubscribed) {
                        _subscribedCommunityIds.value - communityId
                    } else {
                        _subscribedCommunityIds.value + communityId
                    }
                    println("Failed to manage subscription: ${exception.message}")
                }
        }
    }
}