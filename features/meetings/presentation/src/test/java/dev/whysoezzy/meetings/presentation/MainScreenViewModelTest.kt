package dev.whysoezzy.meetings.presentation

import androidx.paging.PagingData
import app.cash.turbine.test
import com.whysoezzy.common.error.ErrorType
import com.whysoezzy.domain.models.MainScreenData
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.SearchData
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.GetPagedMeetingsUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.domain.usecase.SearchMeetingsUseCase
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.testing.MainDispatcherRule
import com.whysoezzy.testing.TestDispatcherProvider
import dev.whysoezzy.uikit.models.UIKitTagState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMainScreenDataUseCase: GetMainScreenDataUseCase = mockk()
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase = mockk()

    private val getPagedMeetingsUseCase: GetPagedMeetingsUseCase = mockk()
    private val searchMeetingsUseCase: SearchMeetingsUseCase = mockk()
    private val testDispatchers = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    @Before
    fun setUp() {
        // pager по умолчанию пустой — тесты Success/Error не падают на подписке pagedMeetings
        every { getPagedMeetingsUseCase(any()) } returns flowOf(PagingData.empty())
        // поиск по умолчанию — пусто; конкретные тесты переопределяют
        coEvery { searchMeetingsUseCase(any()) } returns Result.success(
            SearchData(
                meetings = emptyList(),
                communities = emptyList(),
            ),
        )
    }

    // ==================== loadData ====================

    @Test
    fun `loadData success emits Success state with mapped lists`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            dispatchers = testDispatchers,
        )

        viewModel.uiState.test {
            // Стартовое значение — Loading (set в init)
            assertEquals(MainScreenUiState.Loading, awaitItem())
            advanceUntilIdle()

            val success = awaitItem()
            assertTrue(success is MainScreenUiState.Success)
            success as MainScreenUiState.Success
            assertEquals(0, success.allMeetings.size)
            assertEquals(1, success.heroMeetings.size)
            assertEquals(1, success.popularMeetings.size)
            assertEquals(1, success.categories.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadData failure emits Error state with user-friendly message`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.failure(
            ApiException.NetworkError("connection lost"),
        )

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            dispatchers = testDispatchers,
        )

        viewModel.uiState.test {
            assertEquals(MainScreenUiState.Loading, awaitItem())
            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(error is MainScreenUiState.Error)
            assertEquals(ErrorType.NoConnection, (error as MainScreenUiState.Error).errorType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== search ====================

    @Test
    fun `search delegates to searchMeetingsUseCase and puts results in state`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)
        coEvery { searchMeetingsUseCase("kotlin") } returns
            Result.success(SearchData(meetings = listOf(meeting1), communities = emptyList()))

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            dispatchers = testDispatchers,
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.Search("kotlin"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(1, state.allMeetings.size)
        assertEquals("kotlin", state.searchQuery)
    }

    @Test
    fun `blank query clears search results - paged list shown`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)
        coEvery { searchMeetingsUseCase("kotlin") } returns
            Result.success(SearchData(meetings = listOf(meeting1), communities = emptyList()))

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            dispatchers = testDispatchers,
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.Search("kotlin"))
        advanceUntilIdle()
        viewModel.onEvent(MainScreenEvent.Search(""))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(0, state.allMeetings.size)
        assertEquals("", state.searchQuery)
    }

    // ==================== filterByTag ====================
    @Test
    fun `filterByTag marks selected category and clears search`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            dispatchers = testDispatchers,
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.FilterByTag(KOTLIN_TAG_ID))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(
            UIKitTagState.SELECTED,
            state.categories.first { it.id == KOTLIN_TAG_ID }.state,
        )
        assertEquals("", state.searchQuery)
    }

    @Test
    fun `filterByTag null clears selection`() = runTest {
        coEvery { getMainScreenDataUseCase() } returns Result.success(sampleData)

        val viewModel = MainScreenViewModel(
            getMainScreenDataUseCase = getMainScreenDataUseCase,
            manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
            getPagedMeetingsUseCase = getPagedMeetingsUseCase,
            searchMeetingsUseCase = searchMeetingsUseCase,
            dispatchers = testDispatchers,
        )
        advanceUntilIdle()

        viewModel.onEvent(MainScreenEvent.FilterByTag(KOTLIN_TAG_ID))
        advanceUntilIdle()
        viewModel.onEvent(MainScreenEvent.FilterByTag(null))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MainScreenUiState.Success
        assertTrue(state.categories.all { it.state == UIKitTagState.ACTIVE })
    }

    // ==================== Fixtures ====================

    private companion object {
        const val KOTLIN_TAG_ID = 1L
        const val ANDROID_TAG_ID = 2L
    }

    private val kotlinTag = MeetingTag(
        id = KOTLIN_TAG_ID,
        text = "Kotlin",
        state = TagState.ACTIVE,
    )

    private val androidTag = MeetingTag(
        id = ANDROID_TAG_ID,
        text = "Android",
        state = TagState.ACTIVE,
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
        capacity = 20,
    )

    private val meeting2 = meeting1.copy(
        id = 2L,
        title = "Android meetup",
        tags = listOf(androidTag),
    )

    private val sampleData = MainScreenData(
        heroMeetings = listOf(meeting1),
        popularMeetings = listOf(meeting2),
        allMeetings = listOf(meeting1, meeting2),
        categories = listOf(kotlinTag),
        communities = emptyList(),
        adBlocks = emptyList(),
    )
}
