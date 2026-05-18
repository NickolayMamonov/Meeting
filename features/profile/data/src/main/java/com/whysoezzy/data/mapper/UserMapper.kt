package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.SocialMediaDto
import com.whysoezzy.data.dto.TagDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.models.User
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class UserMapper {

    private val isoFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )

    fun toDomain(dto: UserProfileDto): User {
        return User(
            id = dto.id,
            name = dto.name,
            surname = dto.surname,
            email = dto.email ?: "",
            city = dto.city ?: "",
            avatar = dto.avatarUrl ?: "",
            phone = dto.phone ?: "",
            bio = dto.description ?: "",
            socialMedias = dto.socialMedias.map { it.toDomain() },
            interests = dto.interests.map { it.toDomain() },
            showCommunities = dto.showCommunities,
            showMeetings = dto.showMeetings,
            notificationsEnabled = dto.notificationsEnabled
        )
    }

    fun toUpdateDto(
        user: User,
        interestIds: List<Long>? = null
    ): UpdateUserDto {
        return UpdateUserDto(
            name = user.name.takeIf { it.isNotEmpty() },
            surname = user.surname.takeIf { it.isNotEmpty() },
            email = user.email.takeIf { it.isNotEmpty() },
            city = user.city.takeIf { it.isNotEmpty() },
            description = user.bio.takeIf { it.isNotEmpty() },
            avatarUrl = user.avatar.takeIf { it.isNotEmpty() },
            interestIds = interestIds,
            socialMedias = user.socialMedias.takeIf { it.isNotEmpty() }?.map { it.toDto() },
            showCommunities = user.showCommunities,
            showMeetings = user.showMeetings,
            notificationsEnabled = user.notificationsEnabled
        )
    }

    fun meetingInfoToDomain(dto: MeetingInfoDto): MeetingInfo {
        return MeetingInfo(
            id = dto.id,
            title = dto.title,
            imageUrl = dto.imageUrl,
            time = parseDateToTimestamp(dto.date),
            address = "",
            tags = emptyList(),
            meetingStatus = MeetingStatus.ACTIVE
        )
    }

    fun communityInfoToDomain(dto: CommunityInfoDto): CommunityInfo {
        return CommunityInfo(
            id = dto.id,
            name = dto.name,
            description = dto.description ?: "",
            imageUrl = dto.imageUrl,
            subscribersCount = dto.subscribersCount ?: 0
        )
    }

    // ==================== Private ====================

    private fun TagDto.toDomain(): Tag = Tag(
        id = id,
        name = name
    )

    private fun SocialMediaDto.toDomain(): SocialMediaInfo = SocialMediaInfo(
        type = mapSocialMediaType(type),
        url = url,
        username = extractUsername(url)
    )

    private fun SocialMediaInfo.toDto(): SocialMediaDto = SocialMediaDto(
        type = type.name.lowercase(),
        url = url
    )

    private fun mapSocialMediaType(platform: String): SocialMediaType {
        return when (platform.uppercase()) {
            "TELEGRAM" -> SocialMediaType.TELEGRAM
            "HABR" -> SocialMediaType.HABR
            "GITHUB" -> SocialMediaType.GITHUB
            "LINKEDIN" -> SocialMediaType.LINKEDIN
            else -> SocialMediaType.TELEGRAM
        }
    }

    private fun extractUsername(url: String): String {
        return url.substringAfterLast("/").takeIf { it.isNotBlank() } ?: url
    }

    /**
     * Парсим строки вида "15 декабря 2024, 19:00" или ISO "2024-12-15T19:00:00".
     * Если не распарсить — возвращаем 0L.
     */
    fun parseDateToTimestamp(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        for (formatter in isoFormatters) {
            try {
                return LocalDateTime.parse(dateString, formatter)
                    .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
            } catch (_: Exception) { }
        }
        return 0L
    }
}
