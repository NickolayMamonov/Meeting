package dev.whysoezzy.meetings.presentation

import app.cash.turbine.test
import com.whysoezzy.domain.models.MainScreenData
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.network.error.ApiException
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
class MainScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMainScreenDataUseCase: GetMainScreenDataUseCase = mockk()
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase = mockk()

    // ==================== loadData ====================

    @Test
    fun `loadData success emits Success state with mapped lists`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )

        viewModel.uiState.test {
            // Стартовое значение — Loading (set в init)
            assertEquals(MainScreenUiState.Loading, awaitItem())
            advanceUntilIdle()

            val success = awaitItem()
            assertTrue(success is MainScreenUiState.Success)
            success as MainScreenUiState.Success
            assertEquals(2, success.allMeetings.size)
            assertEquals(1, success.heroMeetings.size)
            assertEquals(1, success.popularMeetings.size)
            assertEquals(1, success.categories.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadData failure emits Error state with user-friendly message`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.failure(
            ApiException.NetworkError("connection lost")
        )

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )

        viewModel.uiState.test {
            assertEquals(MainScreenUiState.Loading, awaitItem())
            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(error is MainScreenUiState.Error)
            // Сообщение от toUserMessage() — конкретный текст специально не проверяем
            // (R-020 поменяет его на StringRes), главное — что Error-состояние выставлено.
            assertTrue((error as MainScreenUiState.Error).message.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== search ====================

    @Test
    fun `search by title filters cached meetings`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )
        advanceUntilIdle() // дождаться loadMainScreenData из init

        viewModel.onEvent(MainScreenEvent.Search("kotlin"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(1, state.allMeetings.size)
        assertEquals("kotlin", state.searchQuery)
    }

    @Test
    fun `search with blank query restores full cached list`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.Search("kotlin"))
        advanceUntilIdle()
        viewModel.onEvent(MainScreenEvent.Search(""))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(2, state.allMeetings.size)
    }

    // ==================== filterByTag ====================

    @Test
    fun `filterByTag keeps only meetings carrying that tag`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.FilterByTag(KOTLIN_TAG_ID))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(1, state.allMeetings.size)
        assertTrue(state.allMeetings.first().tags.any { it.id == KOTLIN_TAG_ID })
    }

    @Test
    fun `filterByTag with null resets the filter`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.FilterByTag(KOTLIN_TAG_ID))
        advanceUntilIdle()
        viewModel.onEvent(MainScreenEvent.FilterByTag(null))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(2, state.allMeetings.size)
    }

    // ==================== Fixtures ====================

    private companion object {
        const val KOTLIN_TAG_ID = 1L
        const val ANDROID_TAG_ID = 2L
    }

    private val kotlinTag = MeetingTag(
        id = KOTLIN_TAG_ID,
        text = "Kotlin",
        state = TagState.ACTIVE
    )

    private val androidTag = MeetingTag(
        id = ANDROID_TAG_ID,
        text = "Android",
        state = TagState.ACTIVE
    )

    private val meeting1 = Meeting(
        id = 1L,
        imageUrl = "",
        title = "Kotlin meetup",
        description = "About Kotlin",
        time = 0L,
        date = "",
        address = MeetingAddress(address = "Moscow", latitude = 55.7, longitude = 37.6),
        tags = listOf(kotlinTag),
        personHost = null,
        communityHost = null,
        participants = emptyList(),
        meetingStatus = MeetingStatus.ACTIVE,
        isUserInParticipants = false,
        capacity = 20
    )

    private val meeting2 = meeting1.copy(
        id = 2L,
        title = "Android meetup",
        tags = listOf(androidTag)
    )

    private val sampleData = MainScreenData(
        heroMeetings = listOf(meeting1),
        popularMeetings = listOf(meeting2),
        allMeetings = listOf(meeting1, meeting2),
        categories = listOf(kotlinTag),
        communities = emptyList(),
        adBlocks = emptyList()
    )
}