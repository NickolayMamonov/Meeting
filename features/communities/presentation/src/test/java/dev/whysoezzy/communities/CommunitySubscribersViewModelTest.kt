package dev.whysoezzy.communities

import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.testing.MainDispatcherRule
import dev.whysoezzy.communities.subscribers.CommunitySubscribersEvent
import dev.whysoezzy.communities.subscribers.CommunitySubscribersUiState
import dev.whysoezzy.communities.subscribers.CommunitySubscribersViewModel
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
class CommunitySubscribersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCommunityByIdUseCase: GetCommunityByIdUseCase = mockk()
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase = mockk()

    private fun viewModel() = CommunitySubscribersViewModel(
        getCommunityByIdUseCase = getCommunityByIdUseCase,
        getCommunitySubscribersUseCase = getCommunitySubscribersUseCase,
    )

    @Test
    fun `load success maps community name and subscribers`() = runTest {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns Result.success(sampleCommunity)
        coEvery { getCommunitySubscribersUseCase(COMMUNITY_ID) } returns Result.success(sampleSubscribers)

        val vm = viewModel()
        vm.onEvent(CommunitySubscribersEvent.LoadSubscribers(COMMUNITY_ID))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is CommunitySubscribersUiState.Success)
        state as CommunitySubscribersUiState.Success
        assertEquals("Книжный клуб", state.communityName)
        assertEquals(2, state.subscribers.size)
    }

    @Test
    fun `community load failure produces Error state`() = runTest {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns
                Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        vm.onEvent(CommunitySubscribersEvent.LoadSubscribers(COMMUNITY_ID))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CommunitySubscribersUiState.Error)
    }

    @Test
    fun `subscribers load failure produces Error state`() = runTest {
        coEvery { getCommunityByIdUseCase(COMMUNITY_ID) } returns Result.success(sampleCommunity)
        coEvery { getCommunitySubscribersUseCase(COMMUNITY_ID) } returns
                Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        vm.onEvent(CommunitySubscribersEvent.LoadSubscribers(COMMUNITY_ID))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CommunitySubscribersUiState.Error)
    }

    private companion object {
        const val COMMUNITY_ID = 1L

        val sampleCommunity = com.whysoezzy.domain.models.Community(
            id = COMMUNITY_ID,
            name = "Книжный клуб",
            description = "desc",
            imageUrl = "",
            subscribersCount = 2,
            isSubscribed = false,
            tags = emptyList(),
        )

        val sampleSubscribers = listOf(
            Person(id = 10L, name = "Иван", surname = "Петров", avatarUrl = "", bio = "", role = "member"),
            Person(id = 11L, name = "Мария", surname = "Сидорова", avatarUrl = "", bio = "", role = "member"),
        )
    }
}