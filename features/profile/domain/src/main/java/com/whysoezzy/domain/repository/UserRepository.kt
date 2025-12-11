package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.User

interface UserRepository {
    suspend fun getCurrentUser(): Result<User>
    suspend fun updateUserProfile(user: User): Result<User>
    suspend fun getUserById(id: Long): Result<User>
}