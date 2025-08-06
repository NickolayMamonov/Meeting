package dev.whysoezzy.profile.details.presentation

sealed class ProfileDetailsUiState {
    object Loading : ProfileDetailsUiState()
    data class Success(
        val userName: String,
        val userEmail: String,
        val avatarUrl: String? = null
    ) : ProfileDetailsUiState()

    data class Error(val message: String) : ProfileDetailsUiState()
}

sealed class ProfileDetailsEvent {
    object LoadProfile : ProfileDetailsEvent()
}