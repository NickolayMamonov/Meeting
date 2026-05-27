package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.User

interface UserRepository {
    suspend fun getCurrentUser(): Result<User>

    suspend fun getUserById(id: Long): Result<User>

    suspend fun updateUserProfile(user: User): Result<User>

    suspend fun getUserMeetings(userId: Long): Result<List<MeetingInfo>>

    suspend fun getUserCommunities(userId: Long): Result<List<CommunityInfo>>
}
