package presentation

import app.cash.turbine.test
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.common.error.AppException
import com.whysoezzy.common.utils.ValidationUtils
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.testing.MainDispatcherRule
import com.whysoezzy.testing.TestDispatcherProvider
import dev.whysoezzy.profile.details.presentation.ProfileDetailsEvent
import dev.whysoezzy.profile.details.presentation.ProfileDetailsNavEvent
import dev.whysoezzy.profile.details.presentation.ProfileDetailsUiState
import dev.whysoezzy.profile.details.presentation.ProfileDetailsViewModel
import dev.whysoezzy.profile.details.presentation.ProfileMode
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
class ProfileDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentUserUseCase: GetCurrentUserUseCase = mockk()
    private val getUserByIdUseCase: GetUserByIdUseCase = mockk()
    private val getUserMeetingsUseCase: GetUserMeetingsUseCase = mockk()
    private val getUserCommunitiesUseCase: GetUserCommunitiesUseCase = mockk()
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase = mockk()
    private val logoutUseCase: LogoutUseCase = mockk(relaxed = true)

    private fun viewModel() = ProfileDetailsViewModel(
        getCurrentUserUseCase = getCurrentUserUseCase,
        getUserByIdUseCase = getUserByIdUseCase,
        getUserMeetingsUseCase = getUserMeetingsUseCase,
        getUserCommunitiesUseCase = getUserCommunitiesUseCase,
        manageCommunitySubscriptionUseCase = manageCommunitySubscriptionUseCase,
        logoutUseCase = logoutUseCase,
        dispatchers = TestDispatcherProvider(mainDispatcherRule.testDispatcher),
    )

    // ==================== loadProfile — own profile ====================

    @Test
    fun `loadProfile null userId loads own profile and emits Success`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ProfileDetailsUiState.Success)
        state as ProfileDetailsUiState.Success
        assertEquals("Иван", state.name)
        assertEquals("Иванов", state.surname)
        assertTrue(state.isOwnProfile)
    }

    @Test
    fun `loadProfile with userId loads other user profile`() = runTest {
        val otherUser = sampleUser.copy(id = 99L, name = "Пётр")
        coEvery { getUserByIdUseCase(99L) } returns Result.success(otherUser)
        coEvery { getUserMeetingsUseCase(99L) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(99L) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Other(99L)))
        advanceUntilIdle()

        val state = vm.uiState.value as ProfileDetailsUiState.Success
        assertEquals("Пётр", state.name)
        assertFalse(state.isOwnProfile)
    }

    // ==================== loadProfile — параллельный coroutineScope ====================

    @Test
    fun `loadProfile loads meetings and communities in parallel and both appear in state`() =
        runTest {
            coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
            coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(sampleMeetings)
            coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(sampleCommunities)

            val vm = viewModel()
            vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
            advanceUntilIdle()

            val state = vm.uiState.value as ProfileDetailsUiState.Success
            assertEquals(2, state.userMeetings.size)
            assertEquals(2, state.userCommunities.size)

            // Оба use case вызваны ровно по одному разу (параллельно, не последовательно)
            coVerify(exactly = 1) { getUserMeetingsUseCase(USER_ID) }
            coVerify(exactly = 1) { getUserCommunitiesUseCase(USER_ID) }
        }

    @Test
    fun `loadProfile shows profile even when meetings request fails`() = runTest {
        // meetings — падает, communities — ок.
        // coroutineScope + getOrNull — ошибка в одном async не ломает другой (R-021)
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns
            Result.failure(RuntimeException("timeout"))
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(sampleCommunities)

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        val state = vm.uiState.value as ProfileDetailsUiState.Success
        assertEquals(0, state.userMeetings.size) // emptyList из getOrNull
        assertEquals(2, state.userCommunities.size) // communities пришли нормально
    }

    // ==================== loadProfile — edge cases ====================

    @Test
    fun `loadProfile own profile with blank name emits NavigateToNameInput`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser.copy(name = ""))

        val vm = viewModel()

        vm.navEvent.test {
            vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
            advanceUntilIdle()

            assertEquals(ProfileDetailsNavEvent.NavigateToNameInput, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadProfile failure emits Error state`() = runTest {
        coEvery { getCurrentUserUseCase() } returns
            Result.failure(RuntimeException("Network error"))

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProfileDetailsUiState.Error)
    }

    @Test
    fun `loadProfile UnauthorizedError logs out without feature navigation`() = runTest {
        coEvery { getCurrentUserUseCase() } returns
            Result.failure(AppException.UnauthorizedError("401"))
        coEvery { logoutUseCase() } returns Unit

        val vm = viewModel()

        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        coVerify(exactly = 1) { logoutUseCase() }
    }

    // ==================== subscription toggle ====================

    @Test
    fun `toggleCommunitySubscription applies optimistic update immediately`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(sampleCommunities)
        coEvery { manageCommunitySubscriptionUseCase(any(), any()) } returns Result.success(Unit)

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        // community id=1 изначально isSubscribed=false
        vm.onEvent(ProfileDetailsEvent.ToggleCommunitySubscription(communityId = 1L, isSubscribed = true))

        // Проверяем сразу — до advanceUntilIdle — оптимистичный апдейт уже применён
        val state = vm.uiState.value as ProfileDetailsUiState.Success
        assertTrue(state.userCommunities.first { it.id == 1L }.isSubscribed)
    }

    @Test
    fun `toggleCommunitySubscription failure rolls back to previous state`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(sampleCommunities)
        coEvery { manageCommunitySubscriptionUseCase(any(), any()) } returns
            Result.failure(RuntimeException("Server error"))

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        vm.onEvent(ProfileDetailsEvent.ToggleCommunitySubscription(communityId = 1L, isSubscribed = true))
        advanceUntilIdle()

        // После ответа сервера — откат к false
        val state = vm.uiState.value as ProfileDetailsUiState.Success
        assertFalse(state.userCommunities.first { it.id == 1L }.isSubscribed)
    }

    @Test
    fun `community isSubscribed is reflected in userCommunities`() = runTest {
        // communities возвращаются с isSubscribed=true для id=2
        val communities = listOf(
            sampleCommunities[0].copy(isSubscribed = false),
            sampleCommunities[1].copy(isSubscribed = true),
        )
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(communities)

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        val state = vm.uiState.value as ProfileDetailsUiState.Success
        assertFalse(state.userCommunities.first { it.id == 1L }.isSubscribed)
        assertTrue(state.userCommunities.first { it.id == 2L }.isSubscribed)
    }

    // ==================== other nav events ====================

    @Test
    fun `Logout clears session without feature navigation`() = runTest {
        coEvery { logoutUseCase() } returns Unit

        val vm = viewModel()

        vm.onEvent(ProfileDetailsEvent.Logout)
        advanceUntilIdle()
    }

    @Test
    fun `ShareProfile emits ShareProfile event with correct name`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getUserMeetingsUseCase(USER_ID) } returns Result.success(emptyList())
        coEvery { getUserCommunitiesUseCase(USER_ID) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.onEvent(ProfileDetailsEvent.LoadProfile(ProfileMode.Self))
        advanceUntilIdle()

        vm.navEvent.test {
            vm.onEvent(ProfileDetailsEvent.ShareProfile)
            advanceUntilIdle()

            val event = awaitItem() as ProfileDetailsNavEvent.ShareProfile
            assertEquals("Иван Иванов", event.name)
            assertTrue(event.shareText.contains("Иван Иванов"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== isValidEmail ====================
    @Test fun `email valid simple`() {
        assertTrue(ValidationUtils.isValidEmail("user@example.com"))
    }

    @Test fun `email valid with dots and plus`() {
        assertTrue(ValidationUtils.isValidEmail("a.b+tag@mail.co.uk"))
    }

    @Test fun `email without at is invalid`() {
        assertFalse(ValidationUtils.isValidEmail("userexample.com"))
    }

    @Test fun `email without domain is invalid`() {
        assertFalse(ValidationUtils.isValidEmail("user@"))
    }

    @Test fun `email without tld is invalid`() {
        assertFalse(ValidationUtils.isValidEmail("user@example"))
    }

    @Test fun `email blank is invalid`() {
        assertFalse(ValidationUtils.isValidEmail(""))
    }
    // ==================== Fixtures ====================

    private companion object {
        const val USER_ID = 1L
    }

    private val sampleUser = User(
        id = USER_ID,
        name = "Иван",
        surname = "Иванов",
        email = "ivan@example.com",
        city = "Москва",
        avatar = "",
        phone = "+79991234567",
        bio = "Bio",
        socialMedias = emptyList(),
        interests = emptyList(),
    )

    private val sampleMeetings = listOf(
        MeetingInfo(id = 10L, title = "Kotlin meetup", imageUrl = "", meetingStatus = MeetingStatus.ACTIVE),
        MeetingInfo(id = 11L, title = "Android conf", imageUrl = "", meetingStatus = MeetingStatus.ACTIVE),
    )

    private val sampleCommunities = listOf(
        CommunityInfo(id = 1L, name = "Kotlin Russia", description = "", imageUrl = "", subscribersCount = 100, isSubscribed = false),
        CommunityInfo(id = 2L, name = "Android Dev", description = "", imageUrl = "", subscribersCount = 200, isSubscribed = false),
    )
}
