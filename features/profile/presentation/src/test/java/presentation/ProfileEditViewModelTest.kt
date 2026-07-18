package presentation

import app.cash.turbine.test
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import com.whysoezzy.domain.usecase.GetAllTagsUseCase
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import com.whysoezzy.testing.MainDispatcherRule
import dev.whysoezzy.profile.edit.presentation.ProfileEditEvent
import dev.whysoezzy.profile.edit.presentation.ProfileEditNavEvent
import dev.whysoezzy.profile.edit.presentation.ProfileEditViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentUserUseCase: GetCurrentUserUseCase = mockk()
    private val updateUserProfileUseCase: UpdateUserProfileUseCase = mockk()
    private val getAllTagsUseCase: GetAllTagsUseCase = mockk()
    private val deleteCurrentUserProfileUseCase: DeleteCurrentUserProfileUseCase = mockk()
    private val logoutUseCase: LogoutUseCase = mockk()

    @Test
    fun `save sends Habr and Telegram social media with their supported URLs`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getAllTagsUseCase() } returns Result.success(emptyList())
        coEvery { updateUserProfileUseCase(any()) } answers { Result.success(firstArg()) }
        val updatedUser = slot<User>()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(SocialMediaType.HABR, "ivan"))
        viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(SocialMediaType.TELEGRAM, "ivan_dev"))
        viewModel.onEvent(ProfileEditEvent.Save)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateUserProfileUseCase(capture(updatedUser)) }
        assertEquals(
            mapOf(
                SocialMediaType.HABR to "https://habr.com/users/ivan",
                SocialMediaType.TELEGRAM to "https://t.me/ivan_dev",
            ),
            updatedUser.captured.socialMedias.associate { it.type to it.url },
        )
    }

    @Test
    fun `clearing a social media username excludes it from save`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getAllTagsUseCase() } returns Result.success(emptyList())
        coEvery { updateUserProfileUseCase(any()) } answers { Result.success(firstArg()) }
        val updatedUser = slot<User>()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(SocialMediaType.HABR, "ivan"))
        viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(SocialMediaType.HABR, ""))
        viewModel.onEvent(ProfileEditEvent.Save)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateUserProfileUseCase(capture(updatedUser)) }
        assertTrue(updatedUser.captured.socialMedias.isEmpty())
    }

    @Test
    fun `confirmed profile deletion logs out and navigates to auth`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getAllTagsUseCase() } returns Result.success(emptyList())
        coEvery { deleteCurrentUserProfileUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.navEvent.test {
            viewModel.onEvent(ProfileEditEvent.DeleteProfile)
            viewModel.onEvent(ProfileEditEvent.ConfirmDeleteProfile)
            advanceUntilIdle()

            assertEquals(ProfileEditNavEvent.NavigateToAuth, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(!viewModel.uiState.value.showDeleteConfirmDialog)
        assertTrue(!viewModel.uiState.value.isSaving)
        coVerifyOrder {
            deleteCurrentUserProfileUseCase()
            logoutUseCase()
        }
    }

    @Test
    fun `failed profile deletion retains session and exposes an error`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(sampleUser)
        coEvery { getAllTagsUseCase() } returns Result.success(emptyList())
        coEvery { deleteCurrentUserProfileUseCase() } returns
            Result.failure(RuntimeException("Network error"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.navEvent.test {
            viewModel.onEvent(ProfileEditEvent.DeleteProfile)
            viewModel.onEvent(ProfileEditEvent.ConfirmDeleteProfile)
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(!viewModel.uiState.value.showDeleteConfirmDialog)
        assertTrue(!viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.error != null)
        coVerify(exactly = 1) { deleteCurrentUserProfileUseCase() }
        coVerify(exactly = 0) { logoutUseCase() }
    }

    private fun viewModel() = ProfileEditViewModel(
        getCurrentUserUseCase = getCurrentUserUseCase,
        updateUserProfileUseCase = updateUserProfileUseCase,
        getAllTagsUseCase = getAllTagsUseCase,
        deleteCurrentUserProfileUseCase = deleteCurrentUserProfileUseCase,
        logoutUseCase = logoutUseCase,
    )

    private val sampleUser = User(
        id = 1L,
        name = "Ivan",
        surname = "Ivanov",
        email = "ivan@example.com",
        city = "Moscow",
        avatar = "",
        phone = "+79991234567",
        bio = "",
    )
}
