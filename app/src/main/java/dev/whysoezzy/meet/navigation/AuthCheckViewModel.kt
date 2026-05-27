package dev.whysoezzy.meet.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AuthCheckViewModel(
    authRepository: AuthRepository,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> =
        authRepository.isLoggedInFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
}
