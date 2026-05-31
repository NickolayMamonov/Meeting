package dev.whysoezzy.auth.presentation.code

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyOtpUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URLEncoder

@OptIn(ExperimentalCoroutinesApi::class)
class CodeVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val verifyOtpUseCase: VerifyOtpUseCase = mockk()
    private val sendOtpUseCase: SendOtpUseCase = mockk()
    private val testPhone = "+79991234567"

    private fun TestScope.viewModel() = CodeVerificationViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(CodeVerificationViewModel.ARG_PHONE to URLEncoder.encode(testPhone, "UTF-8")),
        ),
        verifyOtpUseCase = verifyOtpUseCase,
        sendOtpUseCase = sendOtpUseCase,
        currentTimeMillis = { testScheduler.currentTime },
    )

    // ==================== initial state ====================

    @Test
    fun `initial state has empty code, canResend=false, remainingTime=60`() = runTest {
        val vm = viewModel()
        runCurrent()

        val state = vm.uiState.value
        assertEquals("", state.code)
        assertFalse(state.canResend)
        assertTrue(state.remainingTime > 0)
    }

    // ==================== updateCode ====================

    @Test
    fun `updateCode updates code in state and clears error`() = runTest {
        coEvery { verifyOtpUseCase(any(), any()) } returns Result.failure(RuntimeException("bad"))
        val vm = viewModel()
        // Вызовем ошибку
        vm.onEvent(CodeVerificationEvent.UpdateCode("1234"))
        advanceUntilIdle()
        // Теперь вводим новый код — ошибка должна очиститься
        vm.onEvent(CodeVerificationEvent.UpdateCode("123"))

        val state = vm.uiState.value
        assertEquals("123", state.code)
        assertNull(state.error)
    }

    // ==================== auto-verify on 4 digits ====================

    @Test
    fun `entering 4 digits triggers verifyCode automatically for existing user`() = runTest {
        coEvery { verifyOtpUseCase(testPhone, "1234") } returns Result.success(
            AuthResult(
                accessToken = "token",
                refreshToken = "refresh",
                userId = 1L,
                isNewUser = false,
            ),
        )

        val vm = viewModel()

        vm.navEvent.test {
            vm.onEvent(CodeVerificationEvent.UpdateCode("1234"))
            advanceUntilIdle()

            assertEquals(CodeVerificationNavEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entering 4 digits for new user emits NavigateToNameInput`() = runTest {
        coEvery { verifyOtpUseCase(testPhone, "5678") } returns Result.success(
            AuthResult(
                accessToken = "token",
                refreshToken = "refresh",
                userId = 2L,
                isNewUser = true,
            ),
        )

        val vm = viewModel()

        vm.navEvent.test {
            vm.onEvent(CodeVerificationEvent.UpdateCode("5678"))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is CodeVerificationNavEvent.NavigateToNameInput)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `verify failure sets error in state`() = runTest {
        coEvery { verifyOtpUseCase(any(), any()) } returns
                Result.failure(RuntimeException("Invalid code"))

        val vm = viewModel()
        vm.onEvent(CodeVerificationEvent.UpdateCode("0000"))
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ==================== timer ====================

    @Test
    fun `after 60 seconds timer expires and canResend becomes true`() = runTest {
        val vm = viewModel()

        // Прокручиваем виртуальное время на 61 секунду (polling каждые 200ms)
        advanceTimeBy(61_000L)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.canResend)
        assertEquals(0, state.remainingTime)
    }

    // ==================== resendCode ====================

    @Test
    fun `resendCode when canResend=false does nothing`() = runTest {
        val vm = viewModel()
        // canResend=false изначально, таймер не истёк
        vm.onEvent(CodeVerificationEvent.ResendCode)
        advanceUntilIdle()

        coVerify(exactly = 0) { sendOtpUseCase(any()) }
    }

    @Test
    fun `resendCode after timer expires calls sendOtp and resets timer`() = runTest {
        coEvery { sendOtpUseCase(testPhone) } returns Result.success(Unit)

        val vm = viewModel()
        advanceTimeBy(61_000L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canResend)

        vm.onEvent(CodeVerificationEvent.ResendCode)
        runCurrent()

        coVerify(exactly = 1) { sendOtpUseCase(testPhone) }
        assertFalse(vm.uiState.value.canResend)
        assertEquals(60, vm.uiState.value.remainingTime)
    }
}