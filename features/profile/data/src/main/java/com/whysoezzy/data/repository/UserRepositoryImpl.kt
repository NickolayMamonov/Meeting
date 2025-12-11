package com.whysoezzy.data.repository

import com.whysoezzy.data.api.UserApiImpl
import com.whysoezzy.data.mapper.UserMapper
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.network.safeApiCall

class UserRepositoryImpl(
    private val userApi: UserApiImpl,
    private val userMapper: UserMapper
) : UserRepository {
    override suspend fun getCurrentUser(): Result<User> {
        return safeApiCall {
            val response = userApi.getCurrentUser()
            userMapper.toDomain(response)
        }
    }

    override suspend fun updateUserProfile(user: User): Result<User> {
        return safeApiCall {
            val userDto = userMapper.toDto(user)
            val response = userApi.updateUserProfile(userDto)
            userMapper.toDomain(response)
        }
    }

    override suspend fun getUserById(id: Long): Result<User> {
        return safeApiCall {
            val response = userApi.getUserById(id)
            userMapper.toDomain(response)
        }
    }
}