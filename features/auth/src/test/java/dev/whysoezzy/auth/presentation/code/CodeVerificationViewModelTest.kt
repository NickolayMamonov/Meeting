package dev.whysoezzy.auth.presentation.code

import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome
import com.whysoezzy.auth.domain.usecase.ClearEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.LoadEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.ResendEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyEmailOtpUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeVerificationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val load: LoadEmailOtpAttemptUseCase = mockk()
    private val resend: ResendEmailOtpUseCase = mockk()
    private val verify: VerifyEmailOtpUseCase = mockk()
    private val clear: ClearEmailOtpAttemptUseCase = mockk(relaxed = true)

    private fun TestScope.viewModel() = CodeVerificationViewModel(
        attemptId = "attempt-1",
        loadAttempt = load,
        resendOtp = resend,
        verifyOtpUseCase = verify,
        clearAttempt = clear,
        currentTimeMillis = { testScheduler.currentTime },
    )

    @Test
    fun `found attempt queues one acknowledged input focus request`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        val viewModel = viewModel()

        runCurrent()
        assertEquals(true, viewModel.uiState.value.inputFocusRequestPending)

        viewModel.onEvent(CodeVerificationEvent.AcknowledgeInputFocusRequest)
        assertEquals(false, viewModel.uiState.value.inputFocusRequestPending)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `missing attempt does not queue input focus request`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.MissingOrExpired
        val viewModel = viewModel()

        runCurrent()

        assertEquals(false, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `six ASCII digits auto submit and navigate for existing user`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        coEvery { verify("attempt-1", "123456", any(), any()) } returns
            EmailOtpVerifyOutcome.ExistingUser
        val viewModel = viewModel()

        viewModel.navEvent.test {
            runCurrent()
            viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
            advanceUntilIdle()

            assertEquals(CodeVerificationNavEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate full code updates do not submit while verification is in flight`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        val verification = CompletableDeferred<EmailOtpVerifyOutcome>()
        coEvery { verify("attempt-1", "123456", any(), any()) } coAnswers {
            verification.await()
        }
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        viewModel.onEvent(CodeVerificationEvent.VerifyCode)
        runCurrent()

        coVerify(exactly = 1) { verify("attempt-1", "123456", any(), any()) }

        verification.complete(EmailOtpVerifyOutcome.ExistingUser)
        advanceUntilIdle()
    }

    @Test
    fun `missing resend navigates back to email instead of starting another timer`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns
            EmailOtpResendOutcome.Failed(null, AuthFailure.MissingOrExpiredAttempt)
        val viewModel = viewModel()

        viewModel.navEvent.test {
            runCurrent()
            viewModel.onEvent(CodeVerificationEvent.ResendCode)
            advanceUntilIdle()
            assertEquals(CodeVerificationNavEvent.NavigateToEmail, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed resend uses persisted cooldown deadline`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Failed(
            null,
            AuthFailure.ResendNotAvailable(60_000),
        )
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        runCurrent()

        assertEquals(60, viewModel.uiState.value.remainingTime)
        assertEquals(false, viewModel.uiState.value.canResend)
        assertEquals(AuthFailure.ResendNotAvailable(60_000), viewModel.uiState.value.error)
    }

    @Test
    fun `resend rebinds verification to the durable replacement attempt`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Confirmed(
            EmailOtpAttempt("attempt-2", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        coEvery { verify("attempt-2", "123456", any(), any()) } returns
            EmailOtpVerifyOutcome.ExistingUser
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()

        coVerify(exactly = 1) { verify("attempt-2", "123456", any(), any()) }
        coVerify(exactly = 0) { verify("attempt-1", "123456", any(), any()) }
    }

    @Test
    fun `confirmed resend clears populated code and queues first-cell focus`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Confirmed(
            EmailOtpAttempt("attempt-2", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        coEvery { verify("attempt-1", "123456", any(), any()) } returns
            EmailOtpVerifyOutcome.Failed(AuthFailure.Server)
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.AcknowledgeInputFocusRequest)
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.code)
        assertEquals(true, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `confirmed resend from empty still queues first-cell focus`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Confirmed(
            EmailOtpAttempt("attempt-2", "p***@example.com", 60_000, true, DispatchOutcome.Confirmed),
        )
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.AcknowledgeInputFocusRequest)
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.code)
        assertEquals(true, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `unconfirmed resend retains code and does not queue focus`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Unconfirmed(
            EmailOtpAttempt("attempt-2", "p***@example.com", 60_000, true, DispatchOutcome.Unconfirmed),
        )
        coEvery { verify("attempt-1", "123456", any(), any()) } returns
            EmailOtpVerifyOutcome.Failed(AuthFailure.Server)
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.AcknowledgeInputFocusRequest)
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()

        assertEquals("123456", viewModel.uiState.value.code)
        assertEquals(false, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `resend failure retains code and does not queue focus`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { resend("attempt-1") } returns EmailOtpResendOutcome.Failed(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
            AuthFailure.Server,
        )
        coEvery { verify("attempt-1", "123456", any(), any()) } returns
            EmailOtpVerifyOutcome.Failed(AuthFailure.Server)
        val viewModel = viewModel()

        runCurrent()
        viewModel.onEvent(CodeVerificationEvent.AcknowledgeInputFocusRequest)
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()
        viewModel.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()

        assertEquals("123456", viewModel.uiState.value.code)
        assertEquals(false, viewModel.uiState.value.inputFocusRequestPending)
    }

    @Test
    fun `recoverable verify failure retains code and allows edit based retry`() = runTest {
        coEvery { load("attempt-1") } returns EmailOtpAttemptResult.Found(
            EmailOtpAttempt("attempt-1", "p***@example.com", 0, true, DispatchOutcome.Confirmed),
        )
        coEvery { verify("attempt-1", "123456", any(), any()) } returnsMany listOf(
            EmailOtpVerifyOutcome.Failed(AuthFailure.Server),
            EmailOtpVerifyOutcome.ExistingUser,
        )
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()
        assertEquals("123456", viewModel.uiState.value.code)
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("12345"))
        viewModel.onEvent(CodeVerificationEvent.UpdateCode("123456"))
        advanceUntilIdle()
        assertEquals("123456", viewModel.uiState.value.code)
        coVerify(exactly = 2) { verify("attempt-1", "123456", any(), any()) }
    }
}
