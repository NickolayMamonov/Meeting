package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.SocialMediaDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.User

class UserMapper {
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
            socialMedias = dto.socialMedias.map { it.toDomain() }
        )
    }

    fun toUpdateDto(user: User): UpdateUserDto {
        return UpdateUserDto(
            name = user.name,
            surname = user.surname,
            email = user.email.takeIf { it.isNotEmpty() },
            city = user.city.takeIf { it.isNotEmpty() },
            description = user.bio.takeIf { it.isNotEmpty() },
            avatarUrl = user.avatar.takeIf { it.isNotEmpty() }
        )
    }

    fun meetingInfoToDomain(dto: MeetingInfoDto): MeetingInfo {
        return MeetingInfo(
            id = dto.id,
            title = dto.title,
            imageUrl = dto.imageUrl,
            time = 0L, // TODO: парсить date из строки
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

    private fun SocialMediaDto.toDomain(): SocialMediaInfo {
        return SocialMediaInfo(
            type = mapSocialMedia(type),
            url = url,
            username = extractUsername(url)
        )
    }

    private fun mapSocialMedia(platform: String): SocialMediaType {
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
}
