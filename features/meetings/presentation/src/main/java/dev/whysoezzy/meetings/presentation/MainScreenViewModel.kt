package dev.whysoezzy.meetings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.whysoezzy.common.dispatcher.DispatcherProvider
import com.whysoezzy.common.error.toErrorType
import com.whysoezzy.domain.models.SearchData
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.GetPagedMeetingsUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.domain.usecase.SearchMeetingsUseCase
import dev.whysoezzy.meetings.mappers.toUIKitAdBlocks
import dev.whysoezzy.meetings.mappers.toUIKitCommunityInfoList
import dev.whysoezzy.meetings.mappers.toUIKitMeetingInfo
import dev.whysoezzy.meetings.mappers.toUIKitMeetingInfos
import dev.whysoezzy.meetings.mappers.toUIKitMeetingTags
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitTagState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModel(
    private val getMainScreenDataUseCase: GetMainScreenDataUseCase,
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase,
    private val getPagedMeetingsUseCase: GetPagedMeetingsUseCase,
    private val searchMeetingsUseCase: SearchMeetingsUseCase,
    private val dispatchers: DispatcherProvider,
    private val searchDebounceMillis: Long = DEFAULT_SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MainScreenNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<MainScreenNavEvent> = _navEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val activeTagId = MutableStateFlow<Long?>(null)
    private val searchRequests = MutableStateFlow<SearchRequest?>(null)
    private var nextSearchRequestId = 0L
    private var activeSearchRequestId = 0L
    private var lastCanonicalQuery: String? = null

    val pagedMeetings: Flow<PagingData<UIKitMeetingInfo>> =
        activeTagId
            .flatMapLatest { tagId -> getPagedMeetingsUseCase(tagId) }
            .map { pagingData -> pagingData.map { it.toUIKitMeetingInfo() } }
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            searchRequests
                .filterNotNull()
                .flatMapLatest { request ->
                    flow {
                        val query = request.query ?: return@flow
                        delay(searchDebounceMillis)
                        emit(
                            SearchOutcome(
                                request = request,
                                result = searchMeetingsUseCase(query),
                            ),
                        )
                    }
                }.collect { outcome -> applySearchOutcome(outcome) }
        }
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
            is MainScreenEvent.Refresh -> refresh()
        }
    }

    private fun loadMainScreenData() {
        viewModelScope.launch {
            invalidateSearch()
            _uiState.value = MainScreenUiState.Loading

            getMainScreenDataUseCase()
                .onSuccess { data ->
                    val mapped = withContext(dispatchers.default) {
                        MappedHomeData(
                            heroMeetings = data.heroMeetings.toUIKitMeetingInfos(),
                            popularMeetings = data.popularMeetings.toUIKitMeetingInfos(),
                            categories = data.categories.toUIKitMeetingTags(),
                            communities = data.communities.toUIKitCommunityInfoList(),
                            adBlocks = data.adBlocks.toUIKitAdBlocks(),
                        )
                    }

                    _uiState.value = MainScreenUiState.Success(
                        heroMeetings = mapped.heroMeetings,
                        popularMeetings = mapped.popularMeetings,
                        allMeetings = emptyList(),
                        categories = mapped.categories,
                        communities = mapped.communities,
                        adBlocks = mapped.adBlocks,
                    )
                }.onFailure { exception ->
                    _uiState.value = MainScreenUiState.Error(
                        exception.toErrorType(),
                    )
                }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            getMainScreenDataUseCase()
                .onSuccess { data ->
                    val mapped = withContext(dispatchers.default) {
                        MappedHomeData(
                            heroMeetings = data.heroMeetings.toUIKitMeetingInfos(),
                            popularMeetings = data.popularMeetings.toUIKitMeetingInfos(),
                            categories = data.categories.toUIKitMeetingTags(),
                            communities = data.communities.toUIKitCommunityInfoList(),
                            adBlocks = data.adBlocks.toUIKitAdBlocks(),
                        )
                    }
                    invalidateSearch()
                    _uiState.value = MainScreenUiState.Success(
                        heroMeetings = mapped.heroMeetings,
                        popularMeetings = mapped.popularMeetings,
                        allMeetings = emptyList(),
                        categories = mapped.categories,
                        communities = mapped.communities,
                        adBlocks = mapped.adBlocks,
                    )
                }
            _isRefreshing.value = false
        }
    }

    private fun performSearch(query: String) {
        val currentState = _uiState.value as? MainScreenUiState.Success ?: return
        // фиксируем строку запроса в state (top bar)
        _uiState.value = currentState.copy(searchQuery = query)

        if (query.isBlank()) {
            // пустой запрос → показываем paged-список; результаты поиска очищаем
            invalidateSearch()
            _uiState.value = (_uiState.value as MainScreenUiState.Success).copy(allMeetings = emptyList())
            return
        }

        if (lastCanonicalQuery == query) {
            return
        }

        val request = SearchRequest(
            id = ++nextSearchRequestId,
            query = query,
        )
        activeSearchRequestId = request.id
        lastCanonicalQuery = query
        searchRequests.value = request
    }

    private suspend fun applySearchOutcome(outcome: SearchOutcome) {
        val request = outcome.request
        if (request.id != activeSearchRequestId || request.query == null) {
            return
        }

        val latest = _uiState.value as? MainScreenUiState.Success ?: return
        if (latest.searchQuery != request.query) {
            return
        }

        outcome.result
            .onSuccess { searchData ->
                val mapped = withContext(dispatchers.default) {
                    searchData.meetings.toUIKitMeetingInfos()
                }
                if (request.id != activeSearchRequestId) {
                    return@onSuccess
                }
                val current = _uiState.value as? MainScreenUiState.Success ?: return@onSuccess
                if (current.searchQuery == request.query) {
                    _uiState.value = current.copy(allMeetings = mapped)
                }
            }.onFailure {
                val current = _uiState.value as? MainScreenUiState.Success ?: return@onFailure
                if (request.id == activeSearchRequestId && current.searchQuery == request.query) {
                    _uiState.value = current.copy(allMeetings = emptyList())
                }
            }
    }

    private fun invalidateSearch() {
        val request = SearchRequest(
            id = ++nextSearchRequestId,
            query = null,
        )
        activeSearchRequestId = request.id
        lastCanonicalQuery = null
        searchRequests.value = request
    }

    /**
     * Фильтрация встреч по тегу. null = сбросить фильтр.
     * Работает локально по кэшу, не делает запрос к API.
     */
    private fun filterByTag(tagId: Long?) {
        val currentState = _uiState.value as? MainScreenUiState.Success ?: return

        // выбор тега сбрасывает активный поиск (режимы взаимоисключающие)
        invalidateSearch()
        _uiState.value = currentState.copy(
            searchQuery = "",
            allMeetings = emptyList(),
            categories = currentState.categories.map { tag ->
                tag.copy(state = if (tag.id == tagId) UIKitTagState.SELECTED else UIKitTagState.ACTIVE)
            },
        )

        activeTagId.value = tagId
    }

    private data class SearchRequest(
        val id: Long,
        val query: String?,
    )

    private data class SearchOutcome(
        val request: SearchRequest,
        val result: Result<SearchData>,
    )

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
        val heroMeetings: List<UIKitMeetingInfo>,
        val popularMeetings: List<UIKitMeetingInfo>,
        val categories: List<UIKitMeetingTag>,
        val communities: List<UIKitCommunityInfo>,
        val adBlocks: List<UIKitAdBlock>,
    )

    private companion object {
        const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
