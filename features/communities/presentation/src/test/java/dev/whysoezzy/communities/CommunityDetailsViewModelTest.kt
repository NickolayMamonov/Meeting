package dev.whysoezzy.communities

import app.cash.turbine.test
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `successful subscribe and unsubscribe use the community id and update count`() = runTest {
        stubSuccessfulLoad(sampleCommunity.copy(subscribersCount = 2, isSubscribed = false))
        coEvery { subscribeToCommunityUseCase(COMMUNITY_ID) } returns Result.success(Unit)
        coEvery { unsubscribeFromCommunityUseCase(COMMUNITY_ID) } returns Result.success(Unit)

        val vm = viewModel()
        load(vm)
        advanceUntilIdle()

        vm.onEvent(CommunityDetailsEvent.ToggleSubscription)
        advanceUntilIdle()
        val subscribed = vm.uiState.value as CommunityDetailsUiState.Success
        assertTrue(subscribed.isSubscribed)
        assertEquals(3, subscribed.subscribersCount)
        coVerify(exactly = 1) { subscribeToCommunityUseCase(COMMUNITY_ID) }

        vm.onEvent(CommunityDetailsEvent.ToggleSubscription)
        advanceUntilIdle()
        val unsubscribed = vm.uiState.value as CommunityDetailsUiState.Success
        assertFalse(unsubscribed.isSubscribed)
        assertEquals(2, unsubscribed.subscribersCount)
        coVerify(exactly = 1) { unsubscribeFromCommunityUseCase(COMMUNITY_ID) }
    }

    @Test
    fun `failed subscribe restores prior state and count`() = runTest {
        stubSuccessfulLoad(sampleCommunity.copy(subscribersCount = 0, isSubscribed = false))
        coEvery { subscribeToCommunityUseCase(COMMUNITY_ID) } returns
            Result.failure(RuntimeException("subscribe failed"))

        val vm = viewModel()
        load(vm)
        advanceUntilIdle()
        vm.onEvent(CommunityDetailsEvent.ToggleSubscription)
        advanceUntilIdle()

        val state = vm.uiState.value as CommunityDetailsUiState.Success
        assertFalse(state.isSubscribed)
        assertEquals(0, state.subscribersCount)
        coVerify(exactly = 1) { subscribeToCommunityUseCase(COMMUNITY_ID) }
    }

    @Test
    fun `failed unsubscribe restores prior state and never decrements below zero`() = runTest {
        stubSuccessfulLoad(sampleCommunity.copy(subscribersCount = 0, isSubscribed = true))
        coEvery { unsubscribeFromCommunityUseCase(COMMUNITY_ID) } returns
            Result.failure(RuntimeException("unsubscribe failed"))

        val vm = viewModel()
        load(vm)
        advanceUntilIdle()
        vm.onEvent(CommunityDetailsEvent.ToggleSubscription)
        advanceUntilIdle()

        val state = vm.uiState.value as CommunityDetailsUiState.Success
        assertTrue(state.isSubscribed)
        assertEquals(0, state.subscribersCount)
        coVerify(exactly = 1) { unsubscribeFromCommunityUseCase(COMMUNITY_ID) }
    }

    @Test
    fun `share and navigation events retain their existing payloads`() = runTest {
        stubSuccessfulLoad(sampleCommunity.copy(name = "Synthetic community"))
        val vm = viewModel()
        load(vm)
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(CommunityDetailsEvent.ShareCommunity)
            advanceUntilIdle()
            val share = awaitItem() as dev.whysoezzy.communities.details.presentation
                .CommunityDetailsNavEvent.ShareCommunity
            assertEquals("Synthetic community", share.title)
            assertTrue(share.shareText.contains("Synthetic community"))

            vm.onEvent(CommunityDetailsEvent.NavigateToMeeting(101L))
            assertEquals(
                dev.whysoezzy.communities.details.presentation.CommunityDetailsNavEvent
                    .NavigateToMeeting(101L),
                awaitItem(),
            )
            vm.onEvent(CommunityDetailsEvent.NavigateToProfile(202L))
            assertEquals(
                dev.whysoezzy.communities.details.presentation.CommunityDetailsNavEvent
                    .NavigateToProfile(202L),
                awaitItem(),
            )
            vm.onEvent(CommunityDetailsEvent.NavigateToSubscribers)
            assertEquals(
                dev.whysoezzy.communities.details.presentation.CommunityDetailsNavEvent
                    .NavigateToSubscribers,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stubSuccessfulLoad(community: Community) {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns Result.success(community)
        coEvery { getCommunityMeetingsUseCase(COMMUNITY_ID) } returns Result.success(emptyList())
        coEvery { getCommunitySubscribersUseCase(COMMUNITY_ID) } returns Result.success(emptyList())
    }

    private fun load(viewModel: CommunityDetailsViewModel) {
        viewModel.onEvent(CommunityDetailsEvent.LoadCommunity(COMMUNITY_ID))
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
