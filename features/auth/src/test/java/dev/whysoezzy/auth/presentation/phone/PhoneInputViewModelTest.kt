package dev.whysoezzy.auth.presentation.phone

import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneInputViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val sendOtpUseCase: SendOtpUseCase = mockk()

    private fun viewModel() = PhoneInputViewModel(sendOtpUseCase)

    @Test
    fun `11 digits starting 7 is valid, no error`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("+7 (999) 123-45-67"))
        assertTrue(vm.uiState.value.isValid)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `10 digits is valid (aligned with use-case gate)`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("9991234567"))
        assertTrue(vm.uiState.value.isValid)
    }

    @Test fun `11 digits not starting 7 or 8 sets Invalid`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("99991234567"))
        assertEquals(PhoneInputError.Invalid, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isValid)
    }

    @Test fun `partial input shows no error`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("+7 (999) 12"))
        assertNull(vm.uiState.value.error)
    }

    @Test fun `sendCode failure sets Remote error`() = runTest {
        coEvery { sendOtpUseCase(any()) } returns Result.failure(RuntimeException("Server"))
        val vm = viewModel()
        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("79991234567"))
        vm.onEvent(PhoneInputEvent.SendCode)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is PhoneInputError.Remote)
    }

    @Test fun `sendCode normalizes formatted phone to E164`() = runTest {
        coEvery { sendOtpUseCase("+79991234567") } returns Result.success(Unit)
        val vm = viewModel()

        vm.onEvent(PhoneInputEvent.UpdatePhoneNumber("+7 (999) 123-45-67"))
        vm.onEvent(PhoneInputEvent.SendCode)
        advanceUntilIdle()

        coVerify(exactly = 1) { sendOtpUseCase("+79991234567") }
        assertEquals("+79991234567", vm.uiState.value.phoneNumber)
        assertTrue(vm.uiState.value.isCodeSent)
    }
}
