package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.AvatarUpload
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteCurrentUserProfileUseCaseTest {
    @Test
    fun `delegates profile deletion to repository`() = runTest {
        val repository = FakeUserRepository(Result.success(Unit))

        val result = DeleteCurrentUserProfileUseCase(repository)()

        assertTrue(result.isSuccess)
        assertTrue(repository.deleteRequested)
    }

    private class FakeUserRepository(
        private val deleteResult: Result<Unit>,
    ) : UserRepository {
        var deleteRequested = false

        override suspend fun getCurrentUser(): Result<User> = error("Not used")

        override suspend fun getUserById(id: Long): Result<User> = error("Not used")

        override suspend fun updateUserProfile(user: User): Result<User> = error("Not used")

        override suspend fun uploadAvatar(
            upload: AvatarUpload,
            onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
        ): Result<String> = error("Not used")

        override suspend fun deleteCurrentUserProfile(): Result<Unit> {
            deleteRequested = true
            return deleteResult
        }

        override suspend fun getUserMeetings(userId: Long): Result<List<MeetingInfo>> = error("Not used")

        override suspend fun getUserCommunities(userId: Long): Result<List<CommunityInfo>> = error("Not used")
    }
}
