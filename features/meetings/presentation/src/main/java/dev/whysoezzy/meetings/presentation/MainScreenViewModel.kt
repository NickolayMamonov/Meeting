package dev.whysoezzy.meetings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import dev.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import dev.whysoezzy.domain.usecase.SearchUseCase
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

    init {
        loadMainScreenData()
    }

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.LoadData -> loadMainScreenData()
            is MainScreenEvent.Search -> performSearch(event.query)
            is MainScreenEvent.CommunitySubscriptionChanged -> {
                manageCommunitySubscription(event.communityId, event.isSubscribed)
            }
            is MainScreenEvent.Retry -> loadMainScreenData()
        }
    }

    private fun loadMainScreenData() {
        viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading

            getMainScreenDataUseCase()
                .onSuccess { data ->
                    _uiState.value = MainScreenUiState.Success(data)
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
                .onSuccess { searchResults ->
                    _uiState.value = MainScreenUiState.SearchResults(searchResults)
                }
                .onFailure { exception ->
                    _uiState.value = MainScreenUiState.Error(
                        exception.message ?: "Ошибка поиска"
                    )
                }
        }
    }

    private fun manageCommunitySubscription(communityId: Long, isSubscribed: Boolean) {
        viewModelScope.launch {
            manageCommunitySubscriptionUseCase(communityId, isSubscribed)
                .onSuccess {
                    loadMainScreenData()
                }
                .onFailure { exception ->
                    println("Failed to manage subscription: ${exception.message}")
                }
        }
    }
}