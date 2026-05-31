package dev.whysoezzy.communities

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunityMeetingsUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.domain.usecase.SubscribeToCommunityUseCase
import com.whysoezzy.domain.usecase.UnsubscribeFromCommunityUseCase
import com.whysoezzy.testing.MainDispatcherRule
import com.whysoezzy.testing.TestDispatcherProvider
import dev.whysoezzy.communities.details.presentation.CommunityDetailsEvent
import dev.whysoezzy.communities.details.presentation.CommunityDetailsUiState
import dev.whysoezzy.communities.details.presentation.CommunityDetailsViewModel
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
class CommunityDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCommunityByIdUseCase: GetCommunityByIdUseCase = mockk()
    private val getCommunityMeetingsUseCase: GetCommunityMeetingsUseCase = mockk()
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase = mockk()
    private val subscribeToCommunityUseCase: SubscribeToCommunityUseCase = mockk()
    private val unsubscribeFromCommunityUseCase: UnsubscribeFromCommunityUseCase = mockk()

    private fun viewModel() = CommunityDetailsViewModel(
        getCommunityByIdUseCase = getCommunityByIdUseCase,
        getCommunityMeetingsUseCase = getCommunityMeetingsUseCase,
        getCommunitySubscribersUseCase = getCommunitySubscribersUseCase,
        subscribeToCommunityUseCase = subscribeToCommunityUseCase,
        unsubscribeFromCommunityUseCase = unsubscribeFromCommunityUseCase,
        dispatchers = TestDispatcherProvider(mainDispatcherRule.testDispatcher),
        currentTimeMillis = { FIXED_NOW },
    )

    @Test
    fun `load success splits active and past meetings by fixed clock`() = runTest {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns Result.success(sampleCommunity)
        coEvery { getCommunityMeetingsUseCase(COMMUNITY_ID) } returns Result.success(
            listOf(
                meeting(id = 1L, time = FIXED_NOW + 10_000L), // future → active
                meeting(id = 2L, time = FIXED_NOW - 10_000L), // past
            ),
        )
        coEvery { getCommunitySubscribersUseCase(COMMUNITY_ID) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.onEvent(CommunityDetailsEvent.LoadCommunity(COMMUNITY_ID))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is CommunityDetailsUiState.Success)
        state as CommunityDetailsUiState.Success
        assertEquals(1, state.activeMeetings.size)
        assertEquals(1, state.pastMeetings.size)
    }

    @Test
    fun `community load failure produces Error state`() = runTest {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns
            Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        vm.onEvent(CommunityDetailsEvent.LoadCommunity(COMMUNITY_ID))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CommunityDetailsUiState.Error)
    }

    private companion object {
        const val COMMUNITY_ID = 1L
        const val FIXED_NOW = 1_700_000_000_000L

        val sampleCommunity = Community(
            id = COMMUNITY_ID,
            name = "Книжный клуб",
            description = "desc",
            imageUrl = "",
            subscribersCount = 0,
            isSubscribed = false,
            tags = emptyList(),
        )

        fun meeting(id: Long, time: Long) = Meeting(
            id = id,
            imageUrl = "",
            title = "M$id",
            description = "",
            time = time,
            date = "",
            address = MeetingAddress(address = "", latitude = 0.0, longitude = 0.0),
            tags = emptyList(),
            personHost = null,
            communityHost = null,
            participants = emptyList<Person>(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            capacity = 0,
        )
    }
}
