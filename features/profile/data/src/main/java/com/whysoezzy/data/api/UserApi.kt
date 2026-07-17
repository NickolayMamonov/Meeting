package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto

internal interface UserApi {
    suspend fun getCurrentUserProfile(): UserProfileDto

    suspend fun getUserProfile(id: Long): UserProfileDto

    suspend fun updateUserProfile(updateDto: UpdateUserDto): UserProfileDto

    suspend fun deleteCurrentUserProfile()

    suspend fun getUserMeetings(userId: Long): List<MeetingInfoDto>

    suspend fun getUserCommunities(userId: Long): List<CommunityInfoDto>
}
