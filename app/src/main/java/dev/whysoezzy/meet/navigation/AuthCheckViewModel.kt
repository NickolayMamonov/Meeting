package dev.whysoezzy.meet.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.usecase.ClearPendingEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.LoadActiveEmailOtpAttemptUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthCheckViewModel(
    private val authRepository: AuthRepository,
    private val loadActiveAttempt: LoadActiveEmailOtpAttemptUseCase,
    private val clearPendingAttempt: ClearPendingEmailOtpUseCase,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> =
        authRepository.isLoggedInFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private val _pendingAttempt = MutableStateFlow<EmailOtpAttemptResult?>(null)
    val pendingAttempt: StateFlow<EmailOtpAttemptResult?> = _pendingAttempt.asStateFlow()

    init {
        viewModelScope.launch {
            _pendingAttempt.value = loadActiveAttempt()
        }
        viewModelScope.launch {
            authRepository.isLoggedInFlow.collect { loggedIn ->
                if (loggedIn) {
                    clearPendingAttempt()
                    _pendingAttempt.value = EmailOtpAttemptResult.MissingOrExpired
                }
            }
        }
    }
}
