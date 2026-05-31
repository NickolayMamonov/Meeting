package dev.whysoezzy.meetings.participants

import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.usecase.GetMeetingParticipantsUseCase
import com.whysoezzy.testing.MainDispatcherRule
import com.whysoezzy.testing.TestDispatcherProvider
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsEvent
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsUiState
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
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
class MeetingParticipantsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMeetingParticipantsUseCase: GetMeetingParticipantsUseCase = mockk()

    private fun viewModel() = MeetingParticipantsViewModel(
        getMeetingParticipantsUseCase = getMeetingParticipantsUseCase,
        dispatchers = TestDispatcherProvider(mainDispatcherRule.testDispatcher),
    )

    @Test
    fun `load success maps participants to PersonItem`() = runTest {
        coEvery { getMeetingParticipantsUseCase(MEETING_ID) } returns Result.success(
            listOf(
                Person(id = 1L, name = "Иван", surname = "Петров", avatarUrl = "", bio = "", role = "host"),
            ),
        )

        val vm = viewModel()
        vm.onEvent(MeetingParticipantsEvent.LoadParticipants(MEETING_ID))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MeetingParticipantsUiState.Success)
        state as MeetingParticipantsUiState.Success
        assertEquals(1, state.participants.size)
        assertEquals("Иван Петров", state.participants.first().name)   // склейка в маппере
        assertEquals("host", state.participants.first().role)
    }

    @Test
    fun `load failure produces Error state`() = runTest {
        coEvery { getMeetingParticipantsUseCase(MEETING_ID) } returns
                Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        vm.onEvent(MeetingParticipantsEvent.LoadParticipants(MEETING_ID))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MeetingParticipantsUiState.Error)
    }

    private companion object {
        const val MEETING_ID = 42L
    }
}