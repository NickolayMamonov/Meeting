package dev.whysoezzy.auth.presentation.name

import app.cash.turbine.test
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
    private val sessionRepository: AuthSessionRepository = mockk(relaxed = true)

    private fun viewModel(
        mode: NameInputMode = NameInputMode.Onboarding,
        stage: AuthSession.Stage = AuthSession.Stage.NeedsName,
    ): NameInputViewModel {
        coEvery { sessionRepository.read() } returns AuthSession(1L, stage)
        coEvery {
            sessionRepository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            )
        } returns true
        return NameInputViewModel(mode, userProfileUpdater, sessionRepository)
    }

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
            coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
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
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
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
            AuthOutcome.Failure(AuthFailure.Server)
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

    @Test
    fun `profile failure emits no navigation and remains retryable`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns
            AuthOutcome.Failure(AuthFailure.Server)
        val vm = viewModel(NameInputMode.ProfileCompletion, AuthSession.Stage.Ready)
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertTrue(!vm.uiState.value.isSubmitted)
        assertTrue(!vm.uiState.value.isLoading)
        coVerify(exactly = 0) { sessionRepository.compareAndSetStage(any(), any()) }
    }

    @Test
    fun `onboarding orders profile update before durable CAS`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
        val vm = viewModel()
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(NameInputNavEvent.NavigateToSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerifyOrder {
            userProfileUpdater.updateName("Иван", "Иванов")
            sessionRepository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            )
        }
    }

    @Test
    fun `profile completion updates Ready without stage mutation`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
        val vm = viewModel(NameInputMode.ProfileCompletion, AuthSession.Stage.Ready)
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(NameInputNavEvent.NavigateToProfile, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { sessionRepository.compareAndSetStage(any(), any()) }
    }

    @Test
    fun `unexpected stage resolves without profile IO`() = runTest {
        val vm = viewModel(NameInputMode.Onboarding, AuthSession.Stage.Ready)
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(
                NameInputNavEvent.ResolveFromDurableSession(AuthSession(1L, AuthSession.Stage.Ready)),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { userProfileUpdater.updateName(any(), any()) }
    }

    @Test
    fun `concurrent Welcome is accepted through durable resolution`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
        val vm = viewModel()
        coEvery {
            sessionRepository.read()
        } returnsMany listOf(
            AuthSession(1L, AuthSession.Stage.NeedsName),
            AuthSession(1L, AuthSession.Stage.Welcome),
        )
        coEvery {
            sessionRepository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            )
        } returns false
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(
                NameInputNavEvent.ResolveFromDurableSession(
                    AuthSession(1L, AuthSession.Stage.Welcome),
                ),
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `CAS failure with unchanged NeedsName does not enter auth success`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
        val vm = viewModel()
        coEvery {
            sessionRepository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            )
        } returns false
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(
                NameInputNavEvent.ResolveFromDurableSession(
                    AuthSession(1L, AuthSession.Stage.NeedsName),
                ),
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `profile recheck resolves a concurrent stage change without stage write`() = runTest {
        coEvery { userProfileUpdater.updateName(any(), any()) } returns AuthOutcome.Success(Unit)
        val vm = viewModel(NameInputMode.ProfileCompletion, AuthSession.Stage.Ready)
        coEvery { sessionRepository.read() } returnsMany listOf(
            AuthSession(1L, AuthSession.Stage.Ready),
            AuthSession(1L, AuthSession.Stage.Welcome),
        )
        vm.onEvent(NameInputEvent.UpdateName("Иван"))
        vm.onEvent(NameInputEvent.UpdateSurname("Иванов"))

        vm.navEvent.test {
            vm.onEvent(NameInputEvent.Continue)
            advanceUntilIdle()
            assertEquals(
                NameInputNavEvent.ResolveFromDurableSession(
                    AuthSession(1L, AuthSession.Stage.Welcome),
                ),
                awaitItem(),
            )
            expectNoEvents()
        }

        coVerify(exactly = 0) { sessionRepository.compareAndSetStage(any(), any()) }
    }
}
