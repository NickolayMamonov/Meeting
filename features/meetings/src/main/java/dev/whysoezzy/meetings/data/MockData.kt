package dev.whysoezzy.meetings.data

import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.domain.models.Community
import dev.whysoezzy.domain.models.CommunityHost
import dev.whysoezzy.domain.models.CommunityInfo
import dev.whysoezzy.domain.models.MainScreenData
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.models.MeetingAddress
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.PersonHost
import dev.whysoezzy.domain.models.SearchResults
import dev.whysoezzy.domain.models.TagState

object MockData {
    
    // Mock Tags
    val mockTags = listOf(
        MeetingTag(1L, "Android", TagState.ACTIVE),
        MeetingTag(2L, "Kotlin", TagState.ACTIVE),
        MeetingTag(3L, "Compose", TagState.ACTIVE),
        MeetingTag(4L, "Backend", TagState.ACTIVE),
        MeetingTag(5L, "iOS", TagState.ACTIVE),
        MeetingTag(6L, "UI/UX", TagState.ACTIVE),
        MeetingTag(7L, "DevOps", TagState.ACTIVE),
        MeetingTag(8L, "Data Science", TagState.ACTIVE)
    )
    
    // Mock Hero Events
    val heroEvents = listOf(
        Meeting(
            id = 1L,
            imageUrl = "https://picsum.photos/400/250?random=1",
            title = "Android Dev Summit 2025",
            description = "Главное событие года для Android разработчиков. Новейшие технологии, лучшие практики и networking с экспертами.",
            time = System.currentTimeMillis() + 86400000, // завтра
            address = MeetingAddress(
                address = "Технопарк Сколково, ул. Нобеля 7",
                latitude = 55.7034,
                longitude = 37.3581
            ),
            tags = listOf(mockTags[0], mockTags[2]), // Android, Compose
            personHost = PersonHost(
                id = 1L,
                name = "Александр",
                surname = "Петров",
                description = "Lead Android Developer в Яндексе",
                imageUrl = "https://picsum.photos/100/100?random=10"
            ),
            communityHost = CommunityHost(
                id = 1L,
                title = "Android Developers Moscow",
                description = "Крупнейшее сообщество Android разработчиков в Москве",
                imageUrl = "https://picsum.photos/100/100?random=20",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "10 августа",
            capacity = 200
        ),
        Meeting(
            id = 2L,
            imageUrl = "https://picsum.photos/400/250?random=2",
            title = "Kotlin Multiplatform Workshop",
            description = "Практический workshop по созданию мультиплатформенных приложений. Изучаем KMP от основ до продвинутых техник.",
            time = System.currentTimeMillis() + 172800000, // через 2 дня
            address = MeetingAddress(
                address = "Digital October, Берсеневская наб. 6, стр. 3",
                latitude = 55.7448,
                longitude = 37.6096
            ),
            tags = listOf(mockTags[1], mockTags[4]), // Kotlin, iOS
            personHost = PersonHost(
                id = 2L,
                name = "Мария",
                surname = "Соколова",
                description = "Kotlin Advocate в JetBrains",
                imageUrl = "https://picsum.photos/100/100?random=11"
            ),
            communityHost = CommunityHost(
                id = 2L,
                title = "Kotlin User Group Moscow",
                description = "Сообщество энтузиастов языка Kotlin",
                imageUrl = "https://picsum.photos/100/100?random=21",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = true,
            date = "11 августа",
            capacity = 50
        ),
        Meeting(
            id = 3L,
            imageUrl = "https://picsum.photos/400/250?random=3",
            title = "UI/UX для мобильных приложений",
            description = "Современные подходы к дизайну интерфейсов. Разбираем тренды 2025 года и создаем пользовательский опыт мирового уровня.",
            time = System.currentTimeMillis() + 259200000, // через 3 дня
            address = MeetingAddress(
                address = "Флакон, ул. Большая Новодмитровская 36",
                latitude = 55.7887,
                longitude = 37.6569
            ),
            tags = listOf(mockTags[5]), // UI/UX
            personHost = PersonHost(
                id = 3L,
                name = "Анна",
                surname = "Дизайнова",
                description = "Head of Design в Тинькофф",
                imageUrl = "https://picsum.photos/100/100?random=12"
            ),
            communityHost = CommunityHost(
                id = 3L,
                title = "UX Moscow",
                description = "Сообщество UX/UI дизайнеров",
                imageUrl = "https://picsum.photos/100/100?random=22",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "12 августа",
            capacity = 80
        )
    )
    
    // Mock Popular Events
    val popularEvents = listOf(
        Meeting(
            id = 4L,
            imageUrl = "https://picsum.photos/300/180?random=4",
            title = "React Native vs Flutter",
            description = "Сравнительный анализ популярных кроссплатформенных решений",
            time = System.currentTimeMillis() + 345600000, // через 4 дня
            address = MeetingAddress(
                address = "Mail.ru Group, ул. Ленинградский пр-т 39, стр. 79",
                latitude = 55.8088,
                longitude = 37.5173
            ),
            tags = listOf(mockTags[0], mockTags[4]), // Android, iOS
            personHost = PersonHost(
                id = 4L,
                name = "Дмитрий",
                surname = "Кроссов",
                description = "Mobile Team Lead",
                imageUrl = "https://picsum.photos/100/100?random=13"
            ),
            communityHost = CommunityHost(
                id = 4L,
                title = "Mobile Development Moscow",
                description = "Сообщество мобильных разработчиков",
                imageUrl = "https://picsum.photos/100/100?random=23",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "14 августа",
            capacity = 120
        ),
        Meeting(
            id = 5L,
            imageUrl = "https://picsum.photos/300/180?random=5",
            title = "Microservices на Kotlin",
            description = "Построение микросервисной архитектуры с использованием Spring Boot и Kotlin",
            time = System.currentTimeMillis() + 432000000, // через 5 дней
            address = MeetingAddress(
                address = "Yandex, ул. Льва Толстого 16",
                latitude = 55.7342,
                longitude = 37.5879
            ),
            tags = listOf(mockTags[1], mockTags[3]), // Kotlin, Backend
            personHost = PersonHost(
                id = 5L,
                name = "Сергей",
                surname = "Бэкендов",
                description = "Senior Backend Developer",
                imageUrl = "https://picsum.photos/100/100?random=14"
            ),
            communityHost = CommunityHost(
                id = 5L,
                title = "Kotlin Server Side",
                description = "Сообщество серверных Kotlin разработчиков",
                imageUrl = "https://picsum.photos/100/100?random=24",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = true,
            date = "15 августа",
            capacity = 60
        ),
        Meeting(
            id = 6L,
            imageUrl = "https://picsum.photos/300/180?random=6",
            title = "DevOps для мобильной разработки",
            description = "CI/CD pipelines, автоматизация тестирования и деплоя мобильных приложений",
            time = System.currentTimeMillis() + 518400000, // через 6 дней
            address = MeetingAddress(
                address = "VK, ул. Хамовнический вал 12",
                latitude = 55.7283,
                longitude = 37.5795
            ),
            tags = listOf(mockTags[6], mockTags[0]), // DevOps, Android
            personHost = PersonHost(
                id = 6L,
                name = "Елена",
                surname = "Автоматова",
                description = "DevOps Engineer",
                imageUrl = "https://picsum.photos/100/100?random=15"
            ),
            communityHost = CommunityHost(
                id = 6L,
                title = "DevOps Moscow",
                description = "Сообщество DevOps инженеров",
                imageUrl = "https://picsum.photos/100/100?random=25",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "16 августа",
            capacity = 70
        )
    )
    
    // Mock Communities
    val recommendedCommunities = listOf(
        Community(
            id = 1L,
            imageUrl = "https://picsum.photos/120/120?random=30",
            title = "Android Developers Moscow",
            tags = listOf(mockTags[0], mockTags[2]), // Android, Compose
            description = "Крупнейшее сообщество Android разработчиков в Москве. Регулярные митапы, воркшопы и networking события.",
            subscribers = emptyList(),
            activeMeetings = emptyList(),
            finishedMeetings = emptyList()
        ),
        Community(
            id = 2L,
            imageUrl = "https://picsum.photos/120/120?random=31",
            title = "Kotlin User Group",
            tags = listOf(mockTags[1], mockTags[3]), // Kotlin, Backend
            description = "Сообщество энтузиастов языка Kotlin. Обсуждаем новости, делимся опытом и изучаем best practices.",
            subscribers = emptyList(),
            activeMeetings = emptyList(),
            finishedMeetings = emptyList()
        ),
        Community(
            id = 3L,
            imageUrl = "https://picsum.photos/120/120?random=32",
            title = "UX Moscow",
            tags = listOf(mockTags[5]), // UI/UX
            description = "Сообщество UX/UI дизайнеров. Изучаем пользовательский опыт и современные дизайн-тренды.",
            subscribers = emptyList(),
            activeMeetings = emptyList(),
            finishedMeetings = emptyList()
        ),
        Community(
            id = 4L,
            imageUrl = "https://picsum.photos/120/120?random=33",
            title = "Data Science Community",
            tags = listOf(mockTags[7]), // Data Science
            description = "Сообщество специалистов по анализу данных и машинному обучению.",
            subscribers = emptyList(),
            activeMeetings = emptyList(),
            finishedMeetings = emptyList()
        ),
        Community(
            id = 5L,
            imageUrl = "https://picsum.photos/120/120?random=34",
            title = "DevOps Moscow",
            tags = listOf(mockTags[6]), // DevOps
            description = "Сообщество DevOps инженеров. Автоматизация, мониторинг и надежность систем.",
            subscribers = emptyList(),
            activeMeetings = emptyList(),
            finishedMeetings = emptyList()
        )
    )
    
    // Mock All Events (для вертикального списка)
    val allEvents = listOf(
        Meeting(
            id = 7L,
            imageUrl = "https://picsum.photos/400/200?random=7",
            title = "Jetpack Compose Performance",
            description = "Оптимизация производительности в Compose приложениях. Разбираем частые ошибки и лучшие практики.",
            time = System.currentTimeMillis() + 604800000, // через неделю
            address = MeetingAddress(
                address = "Google Developer Space, ул. Большая Дмитровка 32",
                latitude = 55.7611,
                longitude = 37.6208
            ),
            tags = listOf(mockTags[0], mockTags[2]), // Android, Compose
            personHost = PersonHost(
                id = 7L,
                name = "Роман",
                surname = "Перформансов",
                description = "Android Performance Engineer",
                imageUrl = "https://picsum.photos/100/100?random=16"
            ),
            communityHost = CommunityHost(
                id = 1L,
                title = "Android Developers Moscow",
                description = "Крупнейшее сообщество Android разработчиков в Москве",
                imageUrl = "https://picsum.photos/100/100?random=20",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "9 августа",
            capacity = 90
        ),
        Meeting(
            id = 8L,
            imageUrl = "https://picsum.photos/400/200?random=8",
            title = "Coroutines и Flow в деталях",
            description = "Глубокое погружение в асинхронное программирование на Kotlin. Продвинутые паттерны и кейсы.",
            time = System.currentTimeMillis() + 691200000, // через 8 дней
            address = MeetingAddress(
                address = "JetBrains Office, ул. Вавилова 19",
                latitude = 55.7047,
                longitude = 37.5616
            ),
            tags = listOf(mockTags[1]), // Kotlin
            personHost = PersonHost(
                id = 8L,
                name = "Игорь",
                surname = "Асинхронов",
                description = "Kotlin Coroutines Expert",
                imageUrl = "https://picsum.photos/100/100?random=17"
            ),
            communityHost = CommunityHost(
                id = 2L,
                title = "Kotlin User Group Moscow",
                description = "Сообщество энтузиастов языка Kotlin",
                imageUrl = "https://picsum.photos/100/100?random=21",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = true,
            date = "10 августа",
            capacity = 40
        ),
        Meeting(
            id = 9L,
            imageUrl = "https://picsum.photos/400/200?random=9",
            title = "Machine Learning на мобильных устройствах",
            description = "TensorFlow Lite, Core ML и ML Kit. Интеграция машинного обучения в мобильные приложения.",
            time = System.currentTimeMillis() + 777600000, // через 9 дней
            address = MeetingAddress(
                address = "Samsung AI Center, ул. Дубининская 90",
                latitude = 55.7167,
                longitude = 37.6346
            ),
            tags = listOf(mockTags[0], mockTags[4], mockTags[7]), // Android, iOS, Data Science
            personHost = PersonHost(
                id = 9L,
                name = "Анастасия",
                surname = "Машинова",
                description = "ML Engineer",
                imageUrl = "https://picsum.photos/100/100?random=18"
            ),
            communityHost = CommunityHost(
                id = 7L,
                title = "Mobile ML Community",
                description = "Сообщество специалистов по ML в мобильной разработке",
                imageUrl = "https://picsum.photos/100/100?random=26",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "11 августа",
            capacity = 60
        ),
        Meeting(
            id = 10L,
            imageUrl = "https://picsum.photos/400/200?random=10",
            title = "Security в Android приложениях",
            description = "Современные подходы к безопасности мобильных приложений. OWASP Mobile Top 10 и защита от угроз.",
            time = System.currentTimeMillis() + 864000000, // через 10 дней
            address = MeetingAddress(
                address = "Kaspersky Lab, ул. Беговая 3А",
                latitude = 55.7825,
                longitude = 37.5515
            ),
            tags = listOf(mockTags[0]), // Android
            personHost = PersonHost(
                id = 10L,
                name = "Виктор",
                surname = "Безопасов",
                description = "Mobile Security Expert",
                imageUrl = "https://picsum.photos/100/100?random=19"
            ),
            communityHost = CommunityHost(
                id = 8L,
                title = "Mobile Security Moscow",
                description = "Сообщество специалистов по мобильной безопасности",
                imageUrl = "https://picsum.photos/100/100?random=27",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "12 августа",
            capacity = 55
        ),
        Meeting(
            id = 11L,
            imageUrl = "https://picsum.photos/400/200?random=11",
            title = "React Native для Android разработчиков",
            description = "Переход с нативной Android разработки на React Native. Сходства, различия и подводные камни.",
            time = System.currentTimeMillis() + 950400000, // через 11 дней
            address = MeetingAddress(
                address = "Facebook Office Moscow, ул. Кузнецкий мост 21/5",
                latitude = 55.7606,
                longitude = 37.6245
            ),
            tags = listOf(mockTags[0], mockTags[4]), // Android, iOS
            personHost = PersonHost(
                id = 11L,
                name = "Алексей",
                surname = "Реактивный",
                description = "React Native Team Lead",
                imageUrl = "https://picsum.photos/100/100?random=20"
            ),
            communityHost = CommunityHost(
                id = 4L,
                title = "Mobile Development Moscow",
                description = "Сообщество мобильных разработчиков",
                imageUrl = "https://picsum.photos/100/100?random=23",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = false,
            date = "13 августа",
            capacity = 75
        ),
        Meeting(
            id = 12L,
            imageUrl = "https://picsum.photos/400/200?random=12",
            title = "Архитектура крупных Android проектов",
            description = "Clean Architecture, модульность и масштабирование. Опыт команды разработки приложения с миллионной аудиторией.",
            time = System.currentTimeMillis() + 1036800000, // через 12 дней
            address = MeetingAddress(
                address = "Avito, ул. Карамышевская наб. 44",
                latitude = 55.7575,
                longitude = 37.5031
            ),
            tags = listOf(mockTags[0]), // Android
            personHost = PersonHost(
                id = 12L,
                name = "Константин",
                surname = "Архитектов",
                description = "Principal Android Engineer",
                imageUrl = "https://picsum.photos/100/100?random=21"
            ),
            communityHost = CommunityHost(
                id = 1L,
                title = "Android Developers Moscow",
                description = "Крупнейшее сообщество Android разработчиков в Москве",
                imageUrl = "https://picsum.photos/100/100?random=20",
                meetingsInfo = emptyList()
            ),
            participants = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE,
            isUserInParticipants = true,
            date = "14 августа",
            capacity = 100
        )
    )
    
    // Mock Ad Blocks
    val adBlocks = listOf(
        AdBlock.CommunityAd(
            id = 1L,
            priority = 1,
            title = "Рекомендуемые сообщества для вас",
            communities = listOf(
                CommunityInfo(
                    id = 9L,
                    title = "Flutter Moscow",
                    imageUrl = "https://picsum.photos/120/120?random=35",
                    tags = listOf(mockTags[0]),
                ),
                CommunityInfo(
                    id = 10L,
                    title = "iOS Developers Moscow",
                    imageUrl = "https://picsum.photos/120/120?random=36",
                    tags = listOf(mockTags[0]),
                ),
                CommunityInfo(
                    id = 11L,
                    title = "Backend Moscow",
                    imageUrl = "https://picsum.photos/120/120?random=37",
                    tags = listOf(mockTags[0]),
                )
            )
        ),
        AdBlock.BannerAd(
            id = 2L,
            priority = 2,
            title = "Новые курсы по Android разработке",
            imageUrl = "https://picsum.photos/400/200?random=50",
            actionText = "Записаться на курс",
            deepLink = "app://courses/android"
        ),
        AdBlock.TextAd(
            id = 3L,
            priority = 3,
            title = "Специальная скидка на конференцию DevFest 2025",
            imageUrl = "https://picsum.photos/400/200?random=51",
            actionText = "Получить скидку 20%",
            deepLink = "app://events/devfest2025"
        ),
        AdBlock.BannerAd(
            id = 4L,
            priority = 4,
            title = "Новая книга \"Modern Android Development\"",
            imageUrl = "https://picsum.photos/400/200?random=52",
            actionText = "Купить со скидкой",
            deepLink = "app://books/modern-android"
        ),
        AdBlock.CommunityAd(
            id = 5L,
            priority = 5,
            title = "Популярные сообщества",
            communities = listOf(
                CommunityInfo(
                    id = 12L,
                    title = "Women in Tech Moscow",
                    imageUrl = "https://picsum.photos/120/120?random=38",
                    tags = listOf(mockTags[0]),
                ),
                CommunityInfo(
                    id = 13L,
                    title = "Startup Founders Moscow",
                    imageUrl = "https://picsum.photos/120/120?random=39",
                    tags = listOf(mockTags[0]),
                )
            )
        )
    )
    
    // Собираем все данные для главного экрана
    val mainScreenData = MainScreenData(
        heroEvents = heroEvents,
        popularEvents = popularEvents,
        recommendedCommunities = recommendedCommunities,
        allEvents = allEvents,
        adBlocks = adBlocks
    )
    
    fun createSearchResults(query: String): SearchResults {
        val filteredEvents = allEvents.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.description.contains(query, ignoreCase = true) ||
            it.tags.any { tag -> tag.text.contains(query, ignoreCase = true) }
        }
        
        val filteredCommunities = recommendedCommunities.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.tags.any { tag -> tag.text.contains(query, ignoreCase = true) }
        }
        
        return SearchResults(
            events = filteredEvents,
            communities = filteredCommunities
        )
    }
}