package dev.whysoezzy.auth.presentation.code

import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome
import com.whysoezzy.auth.domain.usecase.ClearEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.LoadEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.ResendEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyEmailOtpUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
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
}
