package com.whysoezzy.data.repository

import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.mapper.toDomain
import com.whysoezzy.data.mapper.toMeetingInfo
import com.whysoezzy.data.mapper.toUpdateDto
import com.whysoezzy.domain.models.AvatarUpload
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.network.safeApiCall

internal class UserRepositoryImpl(
    private val userApi: UserApi,
) : UserRepository {
    override suspend fun getCurrentUser(): Result<User> = safeApiCall {
        userApi.getCurrentUserProfile().toDomain()
    }

    override suspend fun getUserById(id: Long): Result<User> = safeApiCall {
        userApi.getUserProfile(id).toDomain()
    }

    override suspend fun updateUserProfile(user: User): Result<User> = safeApiCall {
        val interestIds = user.interests.map { it.id }.takeIf { it.isNotEmpty() }
        val updateDto = user.toUpdateDto(interestIds)
        userApi.updateUserProfile(updateDto).toDomain()
    }

    override suspend fun uploadAvatar(
        upload: AvatarUpload,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): Result<String> = safeApiCall {
        userApi.uploadAvatar(upload, onProgress).url
    }

    override suspend fun deleteCurrentUserProfile(): Result<Unit> = safeApiCall {
        userApi.deleteCurrentUserProfile()
    }

    override suspend fun getUserMeetings(userId: Long): Result<List<MeetingInfo>> = safeApiCall {
        userApi.getUserMeetings(userId).map { it.toMeetingInfo() }
    }

    override suspend fun getUserCommunities(userId: Long): Result<List<CommunityInfo>> = safeApiCall {
        userApi.getUserCommunities(userId).map { it.toDomain() }
    }
}
