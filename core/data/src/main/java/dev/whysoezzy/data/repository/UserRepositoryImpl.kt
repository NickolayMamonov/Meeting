package dev.whysoezzy.data.repository

import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.SocialMedia
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.domain.models.User
import dev.whysoezzy.domain.repository.UserRepository
import kotlinx.coroutines.delay

class UserRepositoryImpl : UserRepository {

    // Mock current user
    private var currentUser = User(
        id = 1L,
        name = "Иван",
        surname = "Петров",
        phoneNumber = "+7 (999) 123-45-67",
        imageUrl = "https://picsum.photos/150/150?random=100",
        city = "Москва",
        description = "Android разработчик с 5+ летним опытом",
        email = "ivan.petrov@example.com",
        interests = listOf(
            MeetingTag(1L, "Android", TagState.ACTIVE),
            MeetingTag(2L, "Kotlin", TagState.ACTIVE),
            MeetingTag(3L, "Compose", TagState.ACTIVE)
        ),
        socialMedias = mapOf(
            SocialMedia.TELEGRAM to "@ivan_dev",
            SocialMedia.HABR to "ivan_petrov"
        ),
        myMeetings = emptyList(),
        myCommunities = emptyList()
    )

    override suspend fun getCurrentUser(): Result<User> {
        delay(1000) // Simulate network delay
        return Result.success(currentUser)
    }

    override suspend fun updateUserProfile(user: User): Result<User> {
        delay(1500) // Simulate network delay
        currentUser = user
        return Result.success(currentUser)
    }

    override suspend fun getUserById(id: Long): Result<User> {
        delay(1000) // Simulate network delay

        if (id == currentUser.id) {
            return Result.success(currentUser)
        }

        // Mock other user
        val otherUser = User(
            id = id,
            name = "Пользователь",
            surname = "#$id",
            phoneNumber = "+7 (999) 000-00-${id.toString().padStart(2, '0')}",
            imageUrl = "https://picsum.photos/150/150?random=$id",
            city = "Санкт-Петербург",
            description = "Описание пользователя $id",
            email = "user$id@example.com",
            interests = emptyList(),
            socialMedias = emptyMap(),
            myMeetings = emptyList(),
            myCommunities = emptyList()
        )

        return Result.success(otherUser)
    }
}