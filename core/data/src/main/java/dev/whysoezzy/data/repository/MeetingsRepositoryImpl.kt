package dev.whysoezzy.data.repository

import dev.whysoezzy.data.mapper.MeetingMapper
import dev.whysoezzy.data.remote.api.MeetingsApi
import dev.whysoezzy.domain.models.CommunityHost
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.PersonHost
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.domain.repository.MeetingsRepository

class MeetingsRepositoryImpl(
    private val meetingsApi: MeetingsApi,
    private val meetingMapper: MeetingMapper
) : MeetingsRepository {
    override suspend fun getHeroEvents(): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            // val response = meetingsApi.getHeroEvents()
            // val meetings = response.map { meetingMapper.toDomain(it) }
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPopularEvents(): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            // val response = meetingsApi.getPopularEvents()
            // val meetings = response.map { meetingMapper.toDomain(it) }
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllEvents(page: Int, limit: Int): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            // val response = meetingsApi.getAllEvents(page, limit)
            // val meetings = response.map { meetingMapper.toDomain(it) }
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchEvents(query: String): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            // val response = meetingsApi.searchEvents(query)
            // val meetings = response.map { meetingMapper.toDomain(it) }
            Result.success(createMockMeetings().filter {
                it.title.contains(
                    query,
                    ignoreCase = true
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMeetingById(id: Long): Result<Meeting> {
        return try {
            // TODO: Implement API call when backend is ready
            // val response = meetingsApi.getMeetingById(id)
            // val meeting = meetingMapper.toDomain(response)
            val mockMeeting = createMockMeetings().firstOrNull { it.id == id }
                ?: return Result.failure(Exception("Meeting not found"))
            Result.success(mockMeeting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinMeeting(meetingId: Long): Result<Unit> {
        return try {
            // TODO: Implement API call when backend is ready
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveMeeting(meetingId: Long): Result<Unit> {
        return try {
            // TODO: Implement API call when backend is ready
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserMeetings(): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> {
        return try {
            // TODO: Implement API call when backend is ready
            Result.success(createMockMeetings())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createMockMeetings(): List<Meeting> {
        return listOf(
            Meeting(
                id = 1L,
                imageUrl = "https://picsum.photos/300/200?random=1",
                title = "Android Development Meetup",
                description = "Обсуждение новых возможностей Jetpack Compose",
                time = System.currentTimeMillis() + 86400000, // завтра
                address = dev.whysoezzy.domain.models.MeetingAddress(
                    address = "ул. Тверская, 1",
                    latitude = 55.7558,
                    longitude = 37.6176
                ),
                tags = listOf(
                    MeetingTag(
                        id = 1L,
                        text = "Android",
                        state = TagState.ACTIVE
                    ),
                    MeetingTag(
                        id = 2L,
                        text = "Compose",
                        state = TagState.ACTIVE
                    )
                ),
                personHost = PersonHost(
                    id = 1L,
                    name = "Иван",
                    surname = "Иванов",
                    description = "Android разработчик",
                    imageUrl = "https://picsum.photos/100/100?random=1"
                ),
                communityHost = CommunityHost(
                    id = 1L,
                    title = "Android Developers Moscow",
                    description = "Сообщество Android разработчиков",
                    imageUrl = "https://picsum.photos/100/100?random=2",
                    meetingsInfo = emptyList()
                ),
                participants = emptyList(),
                meetingStatus = dev.whysoezzy.domain.models.MeetingStatus.ACTIVE,
                isUserInParticipants = false,
                date = "10 августа",
                capacity = 50
            ),
            Meeting(
                id = 2L,
                imageUrl = "https://picsum.photos/300/200?random=2",
                title = "Kotlin Multiplatform Workshop",
                description = "Изучаем возможности KMP для мобильной разработки",
                time = System.currentTimeMillis() + 172800000, // через 2 дня
                address = dev.whysoezzy.domain.models.MeetingAddress(
                    address = "ул. Арбат, 10",
                    latitude = 55.7522,
                    longitude = 37.5932
                ),
                tags = listOf(
                    MeetingTag(
                        id = 3L,
                        text = "Kotlin",
                        state = TagState.ACTIVE
                    ),
                    MeetingTag(
                        id = 4L,
                        text = "KMP",
                        state = TagState.ACTIVE
                    )
                ),
                personHost = PersonHost(
                    id = 2L,
                    name = "Анна",
                    surname = "Петрова",
                    description = "Kotlin evangelist",
                    imageUrl = "https://picsum.photos/100/100?random=3"
                ),
                communityHost = CommunityHost(
                    id = 2L,
                    title = "Kotlin User Group",
                    description = "Сообщество Kotlin разработчиков",
                    imageUrl = "https://picsum.photos/100/100?random=4",
                    meetingsInfo = emptyList()
                ),
                participants = emptyList(),
                meetingStatus = dev.whysoezzy.domain.models.MeetingStatus.ACTIVE,
                isUserInParticipants = false,
                date = "12 августа",
                capacity = 30
            )
        )
    }
}