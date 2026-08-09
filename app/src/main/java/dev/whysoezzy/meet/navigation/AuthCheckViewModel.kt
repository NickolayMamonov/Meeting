package dev.whysoezzy.meet.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.ClearPendingEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.LoadActiveEmailOtpAttemptUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthCheckViewModel(
    private val authRepository: AuthRepository,
    private val loadActiveAttempt: LoadActiveEmailOtpAttemptUseCase,
    private val clearPendingAttempt: ClearPendingEmailOtpUseCase,
    private val sessionRepository: AuthSessionRepository? = null,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> =
        authRepository.isLoggedInFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val durableSession: StateFlow<AuthSession?> =
        (sessionRepository?.session ?: flowOf(AuthSession.LoggedOut))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private val _pendingAttempt = MutableStateFlow<EmailOtpAttemptResult?>(null)
    val pendingAttempt: StateFlow<EmailOtpAttemptResult?> = _pendingAttempt.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                sessionRepository?.read()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // A corrupt session is resolved as logged out by the store.
            }
        }
        viewModelScope.launch {
            _pendingAttempt.value =
                try {
                    loadActiveAttempt()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Pending OTP is optional recovery state. A corrupted or unavailable
                    // store must not prevent the normal auth graph from starting.
                    EmailOtpAttemptResult.MissingOrExpired
                }
        }
        viewModelScope.launch {
            authRepository.isLoggedInFlow.collect { loggedIn ->
                if (loggedIn) {
                    try {
                        clearPendingAttempt()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // The terminal auth state is still resolved even if cleanup fails.
                    }
                    _pendingAttempt.value = EmailOtpAttemptResult.MissingOrExpired
                }
            }
        }
    }
}
