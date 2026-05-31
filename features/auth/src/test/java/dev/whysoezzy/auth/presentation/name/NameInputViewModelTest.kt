package dev.whysoezzy.auth.presentation.name

import app.cash.turbine.test
import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NameInputViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userProfileUpdater: UserProfileUpdater = mockk()

    private fun viewModel() = NameInputViewModel(userProfileUpdater)

    // ==================== updateName / updateSurname ====================

    @Test
    fun `updateName clears nameError and updates name in state`() = runTest {
        val vm = viewModel()
        // Сначала создаём ошибку через пустой Continue
        vm.onEvent(NameInputEvent.Continue)

        vm.onEvent(NameInputEvent.UpdateName("Иван"))

        val state = vm.uiState.value
        assertEquals("Иван", state.name)
        assertNull(state.nameError)
    }

    @Test
    fun `updateSurname clears surnameError and updates surname in state`() = runTest {
        val vm = viewModel()
        vm.onEvent(NameInputEvent.Continue)

        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        val state = vm.uiState.value
        assertEquals("Иванов", state.surname)
        assertNull(state.surnameError)
    }

    // ==================== validation ====================

    @Test
    fun `Continue with blank name sets nameError`() = runTest {
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.onEvent(NameInputEvent.Continue)

        assertNotNull(vm.uiState.value.nameError)
        assertEquals(NameFieldError.Blank, vm.uiState.value.nameError)
    }

    @Test
    fun `Continue with single-char name sets nameError`() = runTest {
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("И"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.onEvent(NameInputEvent.Continue)

        assertNotNull(vm.uiState.value.nameError)
        assertEquals(NameFieldError.TooShort, vm.uiState.value.nameError)
    }

    @Test
    fun `Continue with digits in name sets nameError`() = runTest {
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("Ив4н"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.onEvent(NameInputEvent.Continue)

        assertNotNull(vm.uiState.value.nameError)
        assertEquals(NameFieldError.NonLetter, vm.uiState.value.nameError)
    }

    @Test
    fun `Continue with blank surname sets surnameError`() = runTest {
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("Иван"))

        vm.onEvent(NameInputEvent.Continue)

        assertNotNull(vm.uiState.value.surnameError)
        assertEquals(NameFieldError.Blank, vm.uiState.value.surnameError)
    }

    // ==================== success submit ====================

    @Test
    fun `Continue with valid name and surname calls updater and emits NavigateToSuccess`() =
        runTest {
            coEvery { userProfileUpdater.updateName(any(), any()) } returns Result.success(Unit)
            val vm = viewModel()
            vm.onEvent(NameInputEvent.UpdateName("Иван"))
            vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

            vm.navEvent.test {
                vm.onEvent(NameInputEvent.Continue)
                advanceUntilIdle()

                assertEquals(NameInputNavEvent.NavigateToSuccess, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { userProfileUpdater.updateName("Иван", "Иванов") }
        }

    @Test
    fun `Continue success sets isSubmitted = true and isLoading = false`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.onEvent(NameInputEvent.Continue)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isSubmitted)
        assertTrue(!state.isLoading)
    }

    // ==================== failure submit ====================

    @Test
    fun `Continue failure sets nameError from server message`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns
            Result.failure(RuntimeException("Server error"))
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.onEvent(NameInputEvent.Continue)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.nameError)
        assertTrue(!state.isLoading)
        assertTrue(vm.uiState.value.nameError is NameFieldError.Remote)
    }
}
