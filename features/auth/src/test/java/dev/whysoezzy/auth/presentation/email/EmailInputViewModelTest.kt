package dev.whysoezzy.auth.presentation.email

import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.usecase.RecoverEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.RequestEmailOtpUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailInputViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val request: RequestEmailOtpUseCase = mockk()
    private val recovery: RecoverEmailOtpAttemptUseCase = mockk()

    @Test
    fun `successful request emits only opaque attempt id`() = runTest {
        coEvery { request("person@example.com") } returns
            EmailOtpRequestOutcome.ProceedToVerification(
                EmailOtpAttempt(
                    attemptId = "attempt-1",
                    maskedEmail = "p***@example.com",
                    resendAvailableAtEpochMillis = 60_000,
                    challengeMayBeActive = true,
                    dispatchOutcome = DispatchOutcome.Confirmed,
                ),
            )
        val viewModel = EmailInputViewModel(request, recovery)

        viewModel.navEvent.test {
            viewModel.onEvent(EmailInputEvent.UpdateEmail("person@example.com"))
            viewModel.onEvent(EmailInputEvent.Submit)
            advanceUntilIdle()

            assertEquals(EmailInputNavEvent.NavigateToCode("attempt-1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate submit while requesting is ignored`() = runTest {
        coEvery { request(any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            EmailOtpRequestOutcome.StayOnEmail(
                EmailOtpAttempt("", "", 0, false, DispatchOutcome.RejectedValidation),
                com.whysoezzy.auth.domain.models.AuthFailure.InvalidEmail,
            )
        }
        val viewModel = EmailInputViewModel(request, recovery)
        viewModel.onEvent(EmailInputEvent.UpdateEmail("bad"))
        viewModel.onEvent(EmailInputEvent.Submit)
        viewModel.onEvent(EmailInputEvent.Submit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error != null)
    }
}
