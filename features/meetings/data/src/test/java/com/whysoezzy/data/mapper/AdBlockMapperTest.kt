package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.AdBlockResponseDto
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.UserInfoDto
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockMapperTest {

    // ==================== COMMUNITIES type ====================

    @Test
    fun `toDomain COMMUNITIES maps id, title, description, isActive`() {
        val dto = communitiesDto()

        val result = dto.toDomain() as AdBlock.CommunitiesAd

        assertEquals(1L, result.id)
        assertEquals("Сообщества", result.title)
        assertEquals("Описание", result.description)
        assertTrue(result.isActive)
    }

    @Test
    fun `toDomain COMMUNITIES maps nested communities list`() {
        val dto = communitiesDto(
            communities = listOf(
                communityInfoDto(id = 10L, name = "Kotlin RU"),
                communityInfoDto(id = 11L, name = "Android Dev"),
            ),
        )

        val result = (dto.toDomain() as AdBlock.CommunitiesAd).communities

        assertEquals(2, result.size)
        assertEquals(10L, result[0].id)
        assertEquals("Kotlin RU", result[0].name)
    }

    @Test
    fun `toDomain COMMUNITIES with null communities maps to emptyList`() {
        val dto = communitiesDto(communities = null)

        val result = (dto.toDomain() as AdBlock.CommunitiesAd).communities

        assertTrue(result.isEmpty())
    }

    // ==================== TEXT type ====================

    @Test
    fun `toDomain TEXT maps actionText and actionUrl`() {
        val dto = textDto(actionText = "Подробнее", actionUrl = "https://example.com")

        val result = dto.toDomain() as AdBlock.TextAd

        assertEquals("Подробнее", result.actionText)
        assertEquals("https://example.com", result.actionUrl)
    }

    @Test
    fun `toDomain TEXT with null actionText and actionUrl maps to null`() {
        val dto = textDto(actionText = null, actionUrl = null)

        val result = dto.toDomain() as AdBlock.TextAd

        assertEquals(null, result.actionText)
        assertEquals(null, result.actionUrl)
    }

    // ==================== PEOPLE type ====================

    @Test
    fun `toDomain PEOPLE maps nested users list`() {
        val dto = peopleDto(
            users = listOf(
                userInfoDto(id = 20L, name = "Иван", surname = "Иванов"),
                userInfoDto(id = 21L, name = "Пётр", surname = "Петров"),
            ),
        )

        val result = (dto.toDomain() as AdBlock.PeopleAd).users

        assertEquals(2, result.size)
        assertEquals(20L, result[0].id)
        assertEquals("Иванов", result[0].surname)
    }

    @Test
    fun `toDomain PEOPLE with null users maps to emptyList`() {
        val dto = peopleDto(users = null)

        val result = (dto.toDomain() as AdBlock.PeopleAd).users

        assertTrue(result.isEmpty())
    }

    // ==================== unknown type ====================

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain unknown type throws IllegalArgumentException`() {
        // R-034 — после фикса это поведение изменится на null + mapNotNull.
        // Тест документирует ТЕКУЩИЙ контракт (throw).
        val dto = baseDto(type = "UNKNOWN")
        dto.toDomain()
    }

    // ==================== CommunityInfoDto.toDomain ====================

    @Test
    fun `CommunityInfoDto toDomain maps all fields correctly`() {
        val dto = communityInfoDto(
            id = 5L,
            name = "Kotlin",
            description = "Kotlin community",
            imageUrl = "https://img.com/k.png",
            subscribersCount = 42,
            isSubscribed = true,
        )

        val result: CommunityInfo = dto.toDomain()

        assertEquals(5L, result.id)
        assertEquals("Kotlin", result.name)
        assertEquals("Kotlin community", result.description)
        assertEquals("https://img.com/k.png", result.imageUrl)
        assertEquals(42, result.subscribersCount)
        assertTrue(result.isSubscribed)
    }

    @Test
    fun `CommunityInfoDto toDomain null description maps to empty string`() {
        val dto = communityInfoDto(description = null)

        val result: CommunityInfo = dto.toDomain()

        assertEquals("", result.description)
    }

    @Test
    fun `CommunityInfoDto toDomain null subscribersCount maps to 0`() {
        val dto = communityInfoDto(subscribersCount = null)

        val result: CommunityInfo = dto.toDomain()

        assertEquals(0, result.subscribersCount)
    }

    // ==================== UserInfoDto.toDomain ====================

    @Test
    fun `UserInfoDto toDomain maps all fields correctly`() {
        val dto = userInfoDto(id = 7L, name = "Анна", surname = "Смирнова", role = "MEMBER")

        val result: Person = dto.toDomain()

        assertEquals(7L, result.id)
        assertEquals("Анна", result.name)
        assertEquals("Смирнова", result.surname)
        assertEquals("MEMBER", result.role)
    }

    // ==================== List<AdBlockResponseDto>.toDomain ====================

    @Test
    fun `list toDomain maps each element`() {
        val dtos = listOf(communitiesDto(id = 1L), textDto(id = 2L), peopleDto(id = 3L))

        val result = dtos.toDomain()

        assertEquals(3, result.size)
        assertTrue(result[0] is AdBlock.CommunitiesAd)
        assertTrue(result[1] is AdBlock.TextAd)
        assertTrue(result[2] is AdBlock.PeopleAd)
    }

    // ==================== Fixtures ====================

    private fun baseDto(
        type: String,
        id: Long = 1L,
        isActive: Boolean = true,
        title: String = "Заголовок",
        description: String = "Описание",
    ) = AdBlockResponseDto(
        type = type,
        id = id,
        isActive = isActive,
        title = title,
        description = description,
    )

    private fun communitiesDto(
        id: Long = 1L,
        communities: List<CommunityInfoDto>? = emptyList(),
    ) = baseDto(type = "COMMUNITIES", id = id).copy(communities = communities)

    private fun textDto(
        id: Long = 2L,
        actionText: String? = null,
        actionUrl: String? = null,
    ) = baseDto(type = "TEXT", id = id).copy(actionText = actionText, actionUrl = actionUrl)

    private fun peopleDto(
        id: Long = 3L,
        users: List<UserInfoDto>? = emptyList(),
    ) = baseDto(type = "PEOPLE", id = id).copy(users = users)

    private fun communityInfoDto(
        id: Long = 1L,
        name: String = "Community",
        description: String? = "Desc",
        imageUrl: String = "",
        subscribersCount: Int? = 0,
        isSubscribed: Boolean = false,
    ) = CommunityInfoDto(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        subscribersCount = subscribersCount,
        isSubscribed = isSubscribed,
    )

    private fun userInfoDto(
        id: Long = 1L,
        name: String = "Имя",
        surname: String = "Фамилия",
        avatarUrl: String = "",
        bio: String = "",
        role: String = "USER",
    ) = UserInfoDto(
        id = id,
        name = name,
        surname = surname,
        avatarUrl = avatarUrl,
        bio = bio,
        role = role,
    )
}