package dev.whysoezzy.meet.navigation

import androidx.lifecycle.ViewModel
import com.whysoezzy.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthCheckViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> = MutableStateFlow(authRepository.isLoggedIn())
}