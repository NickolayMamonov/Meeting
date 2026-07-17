package com.whysoezzy.data.repository

import com.whysoezzy.data.api.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryImplTest {
    private val userApi: UserApi = mockk()
    private val repository = UserRepositoryImpl(userApi)

    @Test
    fun `delete current user profile delegates to API`() = runTest {
        coEvery { userApi.deleteCurrentUserProfile() } returns Unit

        val result = repository.deleteCurrentUserProfile()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { userApi.deleteCurrentUserProfile() }
    }

    @Test
    fun `delete current user profile returns API failure`() = runTest {
        coEvery { userApi.deleteCurrentUserProfile() } throws IOException("offline")

        val result = repository.deleteCurrentUserProfile()

        assertTrue(result.isFailure)
    }
}
