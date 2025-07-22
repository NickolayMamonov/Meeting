package dev.whysoezzy.meetings.presentation

import dev.whysoezzy.domain.models.MainScreenData

sealed class MainScreenUiState {
    object Loading : MainScreenUiState()
    data class Success(val data: MainScreenData) : MainScreenUiState()
    data class SearchResults(val results: dev.whysoezzy.domain.models.SearchResults) : MainScreenUiState()
    data class Error(val message: String) : MainScreenUiState()
}