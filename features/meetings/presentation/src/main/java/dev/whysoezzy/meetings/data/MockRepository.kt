package dev.whysoezzy.meetings.data

import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.domain.models.Community
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.repository.AdBlockRepository
import dev.whysoezzy.domain.repository.CommunitiesRepository
import dev.whysoezzy.domain.repository.MeetingsRepository
import kotlinx.coroutines.delay

/**
 * Mock репозиторий для тестирования UI без настоящего API
 * Симулирует сетевые задержки и различные состояния
 */
class MockMeetingsRepository : MeetingsRepository {
    
    override suspend fun getHeroEvents(): Result<List<Meeting>> {
        delay(500) // Симулируем сетевую задержку
        return Result.success(MockData.heroEvents)
    }
    
    override suspend fun getPopularEvents(): Result<List<Meeting>> {
        delay(300)
        return Result.success(MockData.popularEvents)
    }
    
    override suspend fun getAllEvents(page: Int, limit: Int): Result<List<Meeting>> {
        delay(400)
        // Симулируем пагинацию
        val startIndex = page * limit
        val endIndex = minOf(startIndex + limit, MockData.allEvents.size)
        
        return if (startIndex < MockData.allEvents.size) {
            Result.success(MockData.allEvents.subList(startIndex, endIndex))
        } else {
            Result.success(emptyList())
        }
    }
    
    override suspend fun searchEvents(query: String): Result<List<Meeting>> {
        delay(200)
        return Result.success(MockData.createSearchResults(query).events)
    }
    
    override suspend fun getMeetingById(id: Long): Result<Meeting> {
        delay(300)
        val meeting = MockData.allEvents.find { it.id == id }
            ?: MockData.heroEvents.find { it.id == id }
            ?: MockData.popularEvents.find { it.id == id }
        
        return if (meeting != null) {
            Result.success(meeting)
        } else {
            Result.failure(Exception("Meeting with id $id not found"))
        }
    }
    
    override suspend fun joinMeeting(meetingId: Long): Result<Unit> {
        delay(500)
        // Симулируем различные исходы
        return when (meetingId % 10) {
            0L -> Result.failure(Exception("Meeting is full"))
            1L -> Result.failure(Exception("Registration closed"))
            else -> Result.success(Unit)
        }
    }
    
    override suspend fun leaveMeeting(meetingId: Long): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }
    
    override suspend fun getUserMeetings(): Result<List<Meeting>> {
        delay(400)
        // Возвращаем события, где пользователь участвует
        val userMeetings = (MockData.heroEvents + MockData.popularEvents + MockData.allEvents)
            .filter { it.isUserInParticipants }
        return Result.success(userMeetings)
    }
    
    override suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>> {
        delay(350)
        // Фильтруем по тегам (симулируем категории)
        val categoryEvents = MockData.allEvents.filter { meeting ->
            meeting.tags.any { it.id == categoryId }
        }
        return Result.success(categoryEvents)
    }
    
    override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> {
        delay(300)
        val communityEvents = MockData.allEvents.filter { 
            it.communityHost.id == communityId 
        }
        return Result.success(communityEvents)
    }
}

class MockCommunitiesRepository : CommunitiesRepository {
    
    override suspend fun getRecommendedCommunities(): Result<List<Community>> {
        delay(400)
        return Result.success(MockData.recommendedCommunities)
    }
    
    override suspend fun getCommunityById(id: Long): Result<Community> {
        delay(300)
        val community = MockData.recommendedCommunities.find { it.id == id }
        return if (community != null) {
            Result.success(community)
        } else {
            Result.failure(Exception("Community with id $id not found"))
        }
    }
    
    override suspend fun subscribeToCommunity(communityId: Long): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }
    
    override suspend fun unsubscribeFromCommunity(communityId: Long): Result<Unit> {
        delay(400)
        return Result.success(Unit)
    }
    
    override suspend fun searchCommunities(query: String): Result<List<Community>> {
        delay(250)
        return Result.success(MockData.createSearchResults(query).communities)
    }
}

class MockAdBlockRepository : AdBlockRepository {
    
    override suspend fun getAdBlocks(): Result<List<AdBlock>> {
        delay(200)
        return Result.success(MockData.adBlocks)
    }
}

/**
 * Утилиты для создания различных сценариев тестирования
 */
object MockScenarios {
    
    /**
     * Создает репозиторий с ошибкой загрузки
     */
    class ErrorMeetingsRepository : MeetingsRepository {
        override suspend fun getHeroEvents(): Result<List<Meeting>> {
            delay(1000)
            return Result.failure(Exception("Ошибка сети: не удается подключиться к серверу"))
        }
        
        override suspend fun getPopularEvents(): Result<List<Meeting>> {
            delay(500)
            return Result.failure(Exception("Сервер временно недоступен"))
        }
        
        override suspend fun getAllEvents(page: Int, limit: Int): Result<List<Meeting>> {
            delay(800)
            return Result.failure(Exception("Превышено время ожидания"))
        }
        
        override suspend fun searchEvents(query: String): Result<List<Meeting>> {
            delay(300)
            return Result.failure(Exception("Ошибка поиска"))
        }
        
        override suspend fun getMeetingById(id: Long): Result<Meeting> {
            delay(400)
            return Result.failure(Exception("Событие не найдено"))
        }
        
        override suspend fun joinMeeting(meetingId: Long): Result<Unit> {
            delay(600)
            return Result.failure(Exception("Не удалось присоединиться к событию"))
        }
        
        override suspend fun leaveMeeting(meetingId: Long): Result<Unit> {
            delay(400)
            return Result.failure(Exception("Не удалось покинуть событие"))
        }
        
        override suspend fun getUserMeetings(): Result<List<Meeting>> {
            delay(500)
            return Result.failure(Exception("Не удалось загрузить ваши события"))
        }
        
        override suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>> {
            delay(450)
            return Result.failure(Exception("Ошибка загрузки категории"))
        }
        
        override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> {
            delay(400)
            return Result.failure(Exception("Ошибка загрузки событий сообщества"))
        }
    }
    
    /**
     * Создает репозиторий с медленной загрузкой
     */
//    class SlowMeetingsRepository : MockMeetingsRepository() {
//        override suspend fun getHeroEvents(): Result<List<Meeting>> {
//            delay(3000) // 3 секунды
//            return super.getHeroEvents()
//        }
//
//        override suspend fun getPopularEvents(): Result<List<Meeting>> {
//            delay(2500)
//            return super.getPopularEvents()
//        }
//
//        override suspend fun getAllEvents(page: Int, limit: Int): Result<List<Meeting>> {
//            delay(4000)
//            return super.getAllEvents(page, limit)
//        }
//    }
    
    /**
     * Создает репозиторий с пустыми данными
     */
    class EmptyMeetingsRepository : MeetingsRepository {
        override suspend fun getHeroEvents(): Result<List<Meeting>> {
            delay(300)
            return Result.success(emptyList())
        }
        
        override suspend fun getPopularEvents(): Result<List<Meeting>> {
            delay(200)
            return Result.success(emptyList())
        }
        
        override suspend fun getAllEvents(page: Int, limit: Int): Result<List<Meeting>> {
            delay(400)
            return Result.success(emptyList())
        }
        
        override suspend fun searchEvents(query: String): Result<List<Meeting>> {
            delay(150)
            return Result.success(emptyList())
        }
        
        override suspend fun getMeetingById(id: Long): Result<Meeting> {
            delay(300)
            return Result.failure(Exception("Событие не найдено"))
        }
        
        override suspend fun joinMeeting(meetingId: Long): Result<Unit> {
            delay(400)
            return Result.success(Unit)
        }
        
        override suspend fun leaveMeeting(meetingId: Long): Result<Unit> {
            delay(300)
            return Result.success(Unit)
        }
        
        override suspend fun getUserMeetings(): Result<List<Meeting>> {
            delay(350)
            return Result.success(emptyList())
        }
        
        override suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>> {
            delay(300)
            return Result.success(emptyList())
        }
        
        override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> {
            delay(250)
            return Result.success(emptyList())
        }
    }
}