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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val isoFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
)

fun UserProfileDto.toDomain(): User = User(
    id = id,
    name = name,
    surname = surname,
    email = email ?: "",
    city = city ?: "",
    avatar = avatarUrl ?: "",
    phone = phone ?: "",
    bio = description ?: "",
    socialMedias = socialMedias.map { it.toDomain() },
    interests = interests.map { it.toDomain() },
    showCommunities = showCommunities,
    showMeetings = showMeetings,
    notificationsEnabled = notificationsEnabled
)

fun User.toUpdateDto(interestIds: List<Long>? = null): UpdateUserDto = UpdateUserDto(
    name = name.takeIf { it.isNotEmpty() },
    surname = surname.takeIf { it.isNotEmpty() },
    email = email.takeIf { it.isNotEmpty() },
    city = city.takeIf { it.isNotEmpty() },
    description = bio.takeIf { it.isNotEmpty() },
    avatarUrl = avatar.takeIf { it.isNotEmpty() },
    interestIds = interestIds,
    socialMedias = socialMedias.takeIf { it.isNotEmpty() }?.map { it.toDto() },
    showCommunities = showCommunities,
    showMeetings = showMeetings,
    notificationsEnabled = notificationsEnabled
)

/**
 * Локальное имя `toMeetingInfo`, чтобы не конфликтовать с
 * MeetingDto.toDomain в :core:data (другой DTO, но Kotlin
 * import resolver такие случаи плохо различает в split-package).
 */
fun MeetingInfoDto.toMeetingInfo(): MeetingInfo = MeetingInfo(
    id = id,
    title = title,
    imageUrl = imageUrl,
    time = parseDateToTimestamp(date),
    address = "",
    tags = emptyList(),
    meetingStatus = MeetingStatus.ACTIVE
)

/**
 * Локальное имя `toCommunityInfo`: в :features:meetings:data есть своя
 * CommunityInfoDto.toDomain() (R-035 / R-037 — устранение дубликатов
 * в будущем).
 */
fun CommunityInfoDto.toCommunityInfo(): CommunityInfo = CommunityInfo(
    id = id,
    name = name,
    description = description ?: "",
    imageUrl = imageUrl,
    subscribersCount = subscribersCount ?: 0,
    isSubscribed = isSubscribed
)

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

private fun mapSocialMediaType(platform: String): SocialMediaType =
    when (platform.uppercase()) {
        "TELEGRAM" -> SocialMediaType.TELEGRAM
        "HABR" -> SocialMediaType.HABR
        "GITHUB" -> SocialMediaType.GITHUB
        "LINKEDIN" -> SocialMediaType.LINKEDIN
        else -> SocialMediaType.TELEGRAM
    }

private fun extractUsername(url: String): String =
    url.substringAfterLast("/").takeIf { it.isNotBlank() } ?: url

/**
 * Парсит ISO-форматы дат. Если не распарсить — возвращает 0L.
 */
fun parseDateToTimestamp(dateString: String?): Long {
    if (dateString.isNullOrBlank()) return 0L
    for (formatter in isoFormatters) {
        try {
            return LocalDateTime.parse(dateString, formatter)
                .toEpochSecond(ZoneOffset.UTC) * 1000
        } catch (_: Exception) {}
    }
    return 0L
}