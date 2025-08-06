package dev.whysoezzy.profile.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.whysoezzy.profile.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileDetailsViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileDetailsUiState>(ProfileDetailsUiState.Loading)
    val uiState: StateFlow<ProfileDetailsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileDetailsUiState.Loading

            try {
                getUserProfileUseCase()
                    .onSuccess { user ->
                        _uiState.value = ProfileDetailsUiState.Success(
                            userName = "${user.name} ${user.surname}",
                            userEmail = user.email,
                            avatarUrl = user.imageUrl
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = ProfileDetailsUiState.Error(
                            message = exception.message ?: "Не удалось загрузить профиль"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ProfileDetailsUiState.Error(
                    message = e.message ?: "Произошла ошибка"
                )
            }
        }
    }
}