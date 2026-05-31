package dev.whysoezzy.meetings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.common.dispatcher.DispatcherProvider
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.network.toUserMessage
import dev.whysoezzy.meetings.mappers.toUIKitAdBlocks
import dev.whysoezzy.meetings.mappers.toUIKitCommunityInfoList
import dev.whysoezzy.meetings.mappers.toUIKitMeetingInfos
import dev.whysoezzy.meetings.mappers.toUIKitMeetingTags
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitTagState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MainScreenNavEvent {
    data class NavigateToCommunity(
        val communityId: Long,
    ) : MainScreenNavEvent

    data class NavigateToMeeting(
        val meetingId: Long,
    ) : MainScreenNavEvent
}

class MainScreenViewModel(
    private val getMainScreenDataUseCase: GetMainScreenDataUseCase,
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MainScreenNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<MainScreenNavEvent> = _navEvent.asSharedFlow()

    private var cachedAllMeetings: List<UIKitMeetingInfo> = emptyList()

    init {
        loadMainScreenData()
    }

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.LoadData -> loadMainScreenData()
            is MainScreenEvent.Search -> performSearch(event.query)
            is MainScreenEvent.FilterByTag -> filterByTag(event.tagId)
            is MainScreenEvent.CommunitySubscriptionChanged ->
                toggleCommunitySubscription(event.communityId, event.isSubscribed)
            is MainScreenEvent.Retry -> loadMainScreenData()
            is MainScreenEvent.NavigateToCommunity -> viewModelScope.launch {
                _navEvent.emit(MainScreenNavEvent.NavigateToCommunity(event.communityId))
            }
            is MainScreenEvent.NavigateToMeeting -> viewModelScope.launch {
                _navEvent.emit(MainScreenNavEvent.NavigateToMeeting(event.meetingId))
            }
        }
    }

    private fun loadMainScreenData() {
        viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading

            getMainScreenDataUseCase()
                .onSuccess { data ->
                    val mapped = withContext(dispatchers.default) {
                        MappedHomeData(
                            allMeetings = data.allMeetings.toUIKitMeetingInfos(),
                            heroMeetings = data.heroMeetings.toUIKitMeetingInfos(),
                            popularMeetings = data.popularMeetings.toUIKitMeetingInfos(),
                            categories = data.categories.toUIKitMeetingTags(),
                            communities = data.communities.toUIKitCommunityInfoList(),
                            adBlocks = data.adBlocks.toUIKitAdBlocks(),
                        )
                    }

                    cachedAllMeetings = mapped.allMeetings

                    _uiState.value = MainScreenUiState.Success(
                        heroMeetings = mapped.heroMeetings,
                        popularMeetings = mapped.popularMeetings,
                        allMeetings = mapped.allMeetings,
                        categories = mapped.categories,
                        communities = mapped.communities,
                        adBlocks = mapped.adBlocks,
                    )
                }.onFailure { exception ->
                    _uiState.value = MainScreenUiState.Error(
                        exception.toUserMessage(),
                    )
                }
        }
    }

    private fun performSearch(query: String) {
        val currentState = _uiState.value

        if (currentState is MainScreenUiState.Success) {
            _uiState.value = currentState.copy(searchQuery = query)
        }

        if (query.isBlank()) {
            val currentState = _uiState.value as? MainScreenUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(allMeetings = cachedAllMeetings)
            } else {
                loadMainScreenData()
            }
            return
        }

        viewModelScope.launch {
            val lowerQuery = query.lowercase()
            val filtered = withContext(dispatchers.default) {
                cachedAllMeetings.filter { meeting ->
                    meeting.title.lowercase().contains(lowerQuery) ||
                        meeting.tags.any { it.text.lowercase().contains(lowerQuery) } ||
                        meeting.address.lowercase().contains(lowerQuery)
                }
            }
            val latestState = _uiState.value as? MainScreenUiState.Success ?: return@launch
            if (latestState.searchQuery == query) {
                _uiState.value = latestState.copy(allMeetings = filtered)
            }
        }
    }

    /**
     * Фильтрация встреч по тегу. null = сбросить фильтр.
     * Работает локально по кэшу, не делает запрос к API.
     */
    private fun filterByTag(tagId: Long?) {
        val currentState = _uiState.value as? MainScreenUiState.Success ?: return
        viewModelScope.launch {
            val (filtered, updatedCategories) = withContext(dispatchers.default) {
                val filtered = if (tagId == null) {
                    cachedAllMeetings
                } else {
                    cachedAllMeetings.filter { meeting -> meeting.tags.any { it.id == tagId } }
                }
                val updatedCategories = currentState.categories.map { tag ->
                    tag.copy(state = if (tag.id == tagId) UIKitTagState.SELECTED else UIKitTagState.ACTIVE)
                }
                filtered to updatedCategories
            }

            _uiState.value = currentState.copy(
                allMeetings = filtered,
                categories = updatedCategories,
            )
        }
    }

    /**
     * Оптимистичное обновление подписки.
     * При ошибке API — откатываем состояние обратно.
     */
    private fun toggleCommunitySubscription(communityId: Long, isSubscribed: Boolean) {
        updateCommunitySubscriptionInState(communityId, isSubscribed)

        viewModelScope.launch {
            manageCommunitySubscriptionUseCase(communityId, isSubscribed)
                .onFailure {
                    updateCommunitySubscriptionInState(communityId, !isSubscribed)
                }
        }
    }

    private fun updateCommunitySubscriptionInState(communityId: Long, isSubscribed: Boolean) {
        val state = _uiState.value as? MainScreenUiState.Success ?: return
        _uiState.value = state.copy(
            communities = state.communities.map { community ->
                if (community.id == communityId) {
                    community.copy(isSubscribed = isSubscribed)
                } else {
                    community
                }
            },
        )
    }

    private data class MappedHomeData(
        val allMeetings: List<UIKitMeetingInfo>,
        val heroMeetings: List<UIKitMeetingInfo>,
        val popularMeetings: List<UIKitMeetingInfo>,
        val categories: List<UIKitMeetingTag>,
        val communities: List<UIKitCommunityInfo>,
        val adBlocks: List<UIKitAdBlock>,
    )
}
