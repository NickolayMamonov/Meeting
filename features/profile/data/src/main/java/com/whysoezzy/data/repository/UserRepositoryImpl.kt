package com.whysoezzy.data.repository

import com.whysoezzy.data.api.UserApiKtor
import com.whysoezzy.data.mapper.UserMapper
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.network.safeApiCall

class UserRepositoryImpl(
    private val userApi: UserApiKtor,
    private val userMapper: UserMapper
) : UserRepository {

    override suspend fun getCurrentUser(): Result<User> {
        return safeApiCall {
            val response = userApi.getCurrentUserProfile()
            userMapper.toDomain(response)
        }
    }

    override suspend fun getUserById(id: Long): Result<User> {
        return safeApiCall {
            val response = userApi.getUserProfile(id)
            userMapper.toDomain(response)
        }
    }

    override suspend fun updateUserProfile(user: User): Result<User> {
        return safeApiCall {
            val interestIds = user.interests.map { it.id }.takeIf { it.isNotEmpty() }
            val updateDto = userMapper.toUpdateDto(user, interestIds)
            val response = userApi.updateUserProfile(updateDto)
            userMapper.toDomain(response)
        }
    }

    override suspend fun getUserMeetings(userId: Long): Result<List<MeetingInfo>> {
        return safeApiCall {
            val response = userApi.getUserMeetings(userId)
            response.map { userMapper.meetingInfoToDomain(it) }
        }
    }

    override suspend fun getUserCommunities(userId: Long): Result<List<CommunityInfo>> {
        return safeApiCall {
            val response = userApi.getUserCommunities(userId)
            response.map { userMapper.communityInfoToDomain(it) }
        }
    }
}
