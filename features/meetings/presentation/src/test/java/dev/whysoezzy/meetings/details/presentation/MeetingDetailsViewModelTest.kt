package dev.whysoezzy.meetings.details.presentation

import app.cash.turbine.test
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import com.whysoezzy.domain.usecase.JoinMeetingUseCase
import com.whysoezzy.domain.usecase.LeaveMeetingUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMeetingByIdUseCase: GetMeetingByIdUseCase = mockk()
    private val joinMeetingUseCase: JoinMeetingUseCase = mockk()
    private val leaveMeetingUseCase: LeaveMeetingUseCase = mockk()
    private val isLoggedInUseCase: IsLoggedInUseCase = mockk()

    // Управляемый Flow для тестирования реактивного isLoggedIn
    private val isLoggedInFlow = MutableStateFlow(false)

    private fun viewModel(): MeetingDetailsViewModel {
        // IsLoggedInUseCase.invoke() возвращает Flow — мокаем через заготовленный Flow
        every { isLoggedInUseCase.invoke() } returns isLoggedInFlow
        return MeetingDetailsViewModel(
            getMeetingByIdUseCase = getMeetingByIdUseCase,
            joinMeetingUseCase = joinMeetingUseCase,
            leaveMeetingUseCase = leaveMeetingUseCase,
            isLoggedInUseCase = isLoggedInUseCase,
        )
    }

    // ==================== loadMeeting ====================

    @Test
    fun `loadMeeting success emits Success state with correct fields`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MeetingDetailsUiState.Success)
        state as MeetingDetailsUiState.Success
        assertEquals(MEETING_ID, state.meetingId)
        assertEquals("Kotlin meetup", state.title)
        assertEquals(1, state.tags.size)
        assertFalse(state.isUserJoined)
    }

    @Test
    fun `loadMeeting failure emits Error state`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns
                Result.failure(RuntimeException("Network error"))

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MeetingDetailsUiState.Error)
    }

    @Test
    fun `loadMeeting sets Loading state before result arrives`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)

        val vm = viewModel()

        vm.uiState.test {
            assertEquals(MeetingDetailsUiState.Loading, awaitItem())
            vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
            advanceUntilIdle()
            assertTrue(awaitItem() is MeetingDetailsUiState.Success)
            vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
            assertEquals(MeetingDetailsUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is MeetingDetailsUiState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== joinMeeting ====================

    @Test
    fun `joinMeeting when not logged in emits NavigateToAuth`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)
        isLoggedInFlow.value = false

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(MeetingDetailsEvent.JoinMeeting)
            advanceUntilIdle()

            assertEquals(MeetingDetailsNavEvent.NavigateToAuth, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { joinMeetingUseCase(any()) }
    }

    @Test
    fun `joinMeeting when logged in calls use case and sets isUserJoined = true optimistically`() =
        runTest {
            coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)
            coEvery { joinMeetingUseCase(MEETING_ID) } returns Result.success(Unit)
            isLoggedInFlow.value = true

            val vm = viewModel()
            vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
            advanceUntilIdle()

            vm.onEvent(MeetingDetailsEvent.JoinMeeting)
            advanceUntilIdle()

            val state = vm.uiState.value as MeetingDetailsUiState.Success
            assertTrue(state.isUserJoined)
            coVerify(exactly = 1) { joinMeetingUseCase(MEETING_ID) }
        }

    @Test
    fun `joinMeeting failure rolls back isUserJoined to false`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)
        coEvery { joinMeetingUseCase(MEETING_ID) } returns
                Result.failure(RuntimeException("Server error"))
        isLoggedInFlow.value = true

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.onEvent(MeetingDetailsEvent.JoinMeeting)
        advanceUntilIdle()

        val state = vm.uiState.value as MeetingDetailsUiState.Success
        assertFalse(state.isUserJoined)
    }

    @Test
    fun `joinMeeting when already joined does nothing`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns
                Result.success(sampleMeeting.copy(isUserInParticipants = true))
        isLoggedInFlow.value = true

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.onEvent(MeetingDetailsEvent.JoinMeeting)
        advanceUntilIdle()

        coVerify(exactly = 0) { joinMeetingUseCase(any()) }
    }

    // ==================== leaveMeeting ====================

    @Test
    fun `leaveMeeting calls use case and sets isUserJoined = false optimistically`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns
                Result.success(sampleMeeting.copy(isUserInParticipants = true))
        coEvery { leaveMeetingUseCase(MEETING_ID) } returns Result.success(Unit)

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.onEvent(MeetingDetailsEvent.LeaveMeeting)
        advanceUntilIdle()

        val state = vm.uiState.value as MeetingDetailsUiState.Success
        assertFalse(state.isUserJoined)
        coVerify(exactly = 1) { leaveMeetingUseCase(MEETING_ID) }
    }

    @Test
    fun `leaveMeeting failure rolls back isUserJoined to true`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns
                Result.success(sampleMeeting.copy(isUserInParticipants = true))
        coEvery { leaveMeetingUseCase(MEETING_ID) } returns
                Result.failure(RuntimeException("Server error"))

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.onEvent(MeetingDetailsEvent.LeaveMeeting)
        advanceUntilIdle()

        val state = vm.uiState.value as MeetingDetailsUiState.Success
        assertTrue(state.isUserJoined)
    }

    // ==================== navigation events ====================

    @Test
    fun `OpenMap with valid coordinates emits OpenMap nav event`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns
                Result.success(sampleMeeting.copy(address = addressWithCoords))

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(MeetingDetailsEvent.OpenMap)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is MeetingDetailsNavEvent.OpenMap)
            event as MeetingDetailsNavEvent.OpenMap
            assertEquals(55.7, event.latitude, 0.001)
            assertEquals(37.6, event.longitude, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenMap with zero coordinates emits nothing`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)
        // sampleMeeting.address имеет latitude=0.0, longitude=0.0

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(MeetingDetailsEvent.OpenMap)
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ShareMeeting emits ShareMeeting nav event with title and shareText`() = runTest {
        coEvery { getMeetingByIdUseCase(MEETING_ID) } returns Result.success(sampleMeeting)

        val vm = viewModel()
        vm.onEvent(MeetingDetailsEvent.LoadMeeting(MEETING_ID))
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(MeetingDetailsEvent.ShareMeeting)
            advanceUntilIdle()

            val event = awaitItem() as MeetingDetailsNavEvent.ShareMeeting
            assertEquals("Kotlin meetup", event.title)
            assertTrue(event.shareText.contains("Kotlin meetup"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Fixtures ====================

    private companion object {
        const val MEETING_ID = 42L
    }

    private val addressZero = MeetingAddress(address = "Москва", latitude = 0.0, longitude = 0.0)
    private val addressWithCoords = MeetingAddress(address = "Москва", latitude = 55.7, longitude = 37.6)

    private val sampleMeeting = Meeting(
        id = MEETING_ID,
        imageUrl = "",
        title = "Kotlin meetup",
        description = "About Kotlin",
        time = 0L,
        date = "01 июня 2025, 18:00",
        address = addressZero,
        tags = listOf(
            MeetingTag(id = 1L, text = "Kotlin", state = TagState.ACTIVE),
        ),
        personHost = null,
        communityHost = null,
        participants = emptyList(),
        meetingStatus = MeetingStatus.ACTIVE,
        isUserInParticipants = false,
        capacity = 20,
    )
}