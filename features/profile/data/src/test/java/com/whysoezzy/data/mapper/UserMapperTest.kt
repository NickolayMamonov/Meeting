package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.SocialMediaDto
import com.whysoezzy.data.dto.TagDto
import com.whysoezzy.data.dto.UserProfileDto
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.SocialMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMapperTest {
    // ==================== UserProfileDto.toDomain ====================

    @Test
    fun `toDomain maps required fields correctly`() {
        val dto = minimalUserDto()

        val result = dto.toDomain()

        assertEquals(1L, result.id)
        assertEquals("Иван", result.name)
        assertEquals("Иванов", result.surname)
    }

    @Test
    fun `toDomain null optional fields map to empty strings`() {
        val dto = minimalUserDto(email = null, phone = null, city = null, description = null, avatarUrl = null)

        val result = dto.toDomain()

        assertEquals("", result.email)
        assertEquals("", result.phone)
        assertEquals("", result.city)
        assertEquals("", result.bio)
        assertEquals("", result.avatar)
    }

    @Test
    fun `toDomain maps socialMedias list`() {
        val dto = minimalUserDto(
            socialMedias = listOf(
                SocialMediaDto(type = "TELEGRAM", url = "https://t.me/ivan"),
                SocialMediaDto(type = "GITHUB", url = "https://github.com/ivan"),
            ),
        )

        val result = dto.toDomain()

        assertEquals(2, result.socialMedias.size)
        assertEquals(SocialMediaType.TELEGRAM, result.socialMedias[0].type)
        assertEquals(SocialMediaType.GITHUB, result.socialMedias[1].type)
    }

    @Test
    fun `toDomain maps interests list`() {
        val dto = minimalUserDto(
            interests = listOf(
                TagDto(id = 1L, name = "Kotlin"),
                TagDto(id = 2L, name = "Android"),
            ),
        )

        val result = dto.toDomain()

        assertEquals(2, result.interests.size)
        assertEquals("Kotlin", result.interests[0].name)
    }

    @Test
    fun `toDomain empty socialMedias and interests map to emptyList`() {
        val dto = minimalUserDto()

        val result = dto.toDomain()

        assertTrue(result.socialMedias.isEmpty())
        assertTrue(result.interests.isEmpty())
    }

    @Test
    fun `toDomain unknown socialMedia type falls back to TELEGRAM`() {
        val dto = minimalUserDto(
            socialMedias = listOf(SocialMediaDto(type = "UNKNOWN_PLATFORM", url = "https://example.com")),
        )

        val result = dto.toDomain()

        assertEquals(SocialMediaType.TELEGRAM, result.socialMedias[0].type)
    }

    // ==================== MeetingInfoDto.toMeetingInfo — parseDateToTimestamp ====================
    // parseDateToTimestamp — private, тестируем косвенно через toMeetingInfo()

    @Test
    fun `toMeetingInfo parses ISO datetime yyyy-MM-ddTHH-mm-ss correctly`() {
        val dto = MeetingInfoDto(id = 1L, title = "Meet", imageUrl = "", date = "2025-06-01T18:00:00")

        val result = dto.toMeetingInfo()

        // 2025-06-01T18:00:00 UTC → epoch millis
        val expected = java.time.LocalDateTime
            .of(2025, 6, 1, 18, 0, 0)
            .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        assertEquals(expected, result.time)
    }

    @Test
    fun `toMeetingInfo parses ISO date only yyyy-MM-dd correctly`() {
        val dto = MeetingInfoDto(id = 1L, title = "Meet", imageUrl = "", date = "2025-06-01")

        val result = dto.toMeetingInfo()

        val expected = java.time.LocalDateTime
            .of(2025, 6, 1, 0, 0, 0)
            .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        assertEquals(expected, result.time)
    }

    @Test
    fun `toMeetingInfo returns 0L for null date`() {
        val dto = MeetingInfoDto(id = 1L, title = "Meet", imageUrl = "", date = "")

        val result = dto.toMeetingInfo()

        assertEquals(0L, result.time)
    }

    @Test
    fun `toMeetingInfo returns 0L for unparseable date`() {
        val dto = MeetingInfoDto(id = 1L, title = "Meet", imageUrl = "", date = "not-a-date")

        val result = dto.toMeetingInfo()

        assertEquals(0L, result.time)
    }

    @Test
    fun `toMeetingInfo always sets status ACTIVE`() {
        val dto = MeetingInfoDto(id = 1L, title = "Meet", imageUrl = "", date = "2025-06-01")

        val result = dto.toMeetingInfo()

        assertEquals(MeetingStatus.ACTIVE, result.meetingStatus)
    }

    // ==================== Fixtures ====================

    private fun minimalUserDto(
        id: Long = 1L,
        name: String = "Иван",
        surname: String = "Иванов",
        email: String? = "ivan@example.com",
        phone: String? = "+79991234567",
        city: String? = "Москва",
        description: String? = "Bio",
        avatarUrl: String? = "https://img.com/avatar.png",
        interests: List<TagDto> = emptyList(),
        socialMedias: List<SocialMediaDto> = emptyList(),
    ) = UserProfileDto(
        id = id,
        name = name,
        surname = surname,
        email = email,
        phone = phone,
        city = city,
        description = description,
        avatarUrl = avatarUrl,
        interests = interests,
        socialMedias = socialMedias,
    )
}
