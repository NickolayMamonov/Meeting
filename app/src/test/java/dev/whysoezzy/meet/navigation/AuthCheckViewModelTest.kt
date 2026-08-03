package dev.whysoezzy.meet.navigation

import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.usecase.ClearPendingEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.LoadActiveEmailOtpAttemptUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCheckViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val loadPending: LoadActiveEmailOtpAttemptUseCase = mockk()
    private val clearPending: ClearPendingEmailOtpUseCase = mockk(relaxed = true)

    // Управляемый Flow — имитирует TokenManager.isLoggedInFlow
    private val isLoggedInFlow = MutableStateFlow(false)

    private fun viewModel(): AuthCheckViewModel {
        every { authRepository.isLoggedInFlow } returns isLoggedInFlow
        coEvery { loadPending() } returns EmailOtpAttemptResult.MissingOrExpired
        return AuthCheckViewModel(authRepository, loadPending, clearPending)
    }

    // ==================== initial state ====================

    @Test
    fun `initial isLoggedIn value is null before flow emits`() = runTest {
        val vm = viewModel()

        // stateIn с WhileSubscribed — до первой подписки значение = initialValue = null
        vm.isLoggedIn.test {
            assertNull(awaitItem()) // initialValue
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoggedIn emits false when token absent`() = runTest {
        isLoggedInFlow.value = false
        val vm = viewModel()

        vm.isLoggedIn.test {
            assertNull(awaitItem()) // initialValue
            advanceUntilIdle()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoggedIn emits true after login`() = runTest {
        isLoggedInFlow.value = false
        val vm = viewModel()

        vm.isLoggedIn.test {
            assertNull(awaitItem()) // initialValue
            advanceUntilIdle()
            assertEquals(false, awaitItem())

            // Симулируем saveTokens в TokenManager → Flow пересчитывается
            isLoggedInFlow.value = true
            advanceUntilIdle()

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoggedIn emits false after logout`() = runTest {
        isLoggedInFlow.value = true
        val vm = viewModel()

        vm.isLoggedIn.test {
            assertNull(awaitItem()) // initialValue
            advanceUntilIdle()
            assertEquals(true, awaitItem())

            // Симулируем clearTokens в TokenManager
            isLoggedInFlow.value = false
            advanceUntilIdle()

            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoggedIn reflects multiple login-logout cycles`() = runTest {
        isLoggedInFlow.value = false
        val vm = viewModel()

        vm.isLoggedIn.test {
            assertNull(awaitItem())
            advanceUntilIdle()
            assertEquals(false, awaitItem())

            isLoggedInFlow.value = true
            advanceUntilIdle()
            assertEquals(true, awaitItem())

            isLoggedInFlow.value = false
            advanceUntilIdle()
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pending attempt is recovered from durable state`() = runTest {
        val pending = EmailOtpAttempt(
            attemptId = "attempt-1",
            maskedEmail = "p***@example.com",
            resendAvailableAtEpochMillis = 60_000,
            challengeMayBeActive = true,
            dispatchOutcome = DispatchOutcome.Confirmed,
        )
        every { authRepository.isLoggedInFlow } returns isLoggedInFlow
        coEvery { loadPending() } returns EmailOtpAttemptResult.Found(pending)
        val vm = AuthCheckViewModel(authRepository, loadPending, clearPending)

        vm.pendingAttempt.test {
            assertNull(awaitItem())
            advanceUntilIdle()
            assertEquals(EmailOtpAttemptResult.Found(pending), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `authenticated startup clears pending attempt`() = runTest {
        every { authRepository.isLoggedInFlow } returns isLoggedInFlow
        coEvery { loadPending() } returns EmailOtpAttemptResult.MissingOrExpired
        val vm = AuthCheckViewModel(authRepository, loadPending, clearPending)
        vm.isLoggedIn.test {
            awaitItem()
            isLoggedInFlow.value = true
            advanceUntilIdle()
            coVerify(exactly = 1) { clearPending() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
