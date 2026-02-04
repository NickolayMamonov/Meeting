package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.MeetingTagDto
import com.whysoezzy.data.dto.UserDto
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.models.User

class UserMapper {
    fun toDomain(dto: UserDto): User {
        return User(
            id = dto.id,
            name = dto.name,
            surname = dto.surname,
            email = dto.email ?: "",
            city = dto.city,
            avatar = dto.avatar ?: "",
            phone = dto.phone,
            bio = dto.bio,
            interests = dto.interests.map { tagDto ->
                MeetingTag(
                    id = tagDto.id,
                    text = tagDto.text,
                    state = TagState.ACTIVE
                )
            },
            socialMedia = dto.socialMedia.mapKeys { (key, _) ->
                mapSocialMedia(key)
            },
            subscribedCommunities = dto.subscribedCommunities.map { communityDto ->
                CommunityInfo(
                    id = communityDto.id,
                    title = communityDto.name,
                    imageUrl = communityDto.imageUrl
                )
            },
            participatingMeetings = dto.participatingMeetings.map { meetingDto ->
                MeetingInfo(
                    id = meetingDto.id,
                    title = meetingDto.title,
                    imageUrl = meetingDto.imageUrl,
                    time = 0L,
                    tags = emptyList(),
                    address = "",
                    meetingStatus = MeetingStatus.ACTIVE
                )
            }
        )
    }

    fun toDto(user: User): UserDto {
        return UserDto(
            id = user.id,
            name = user.name,
            surname = user.surname,
            email = user.email.takeIf { it.isNotEmpty() },
            city = user.city,
            avatar = user.avatar.takeIf { it.isNotEmpty() },
            phone = user.phone,
            bio = user.bio,
            interests = user.interests.map { tag ->
                MeetingTagDto(
                    id = tag.id,
                    text = tag.text
                )
            },
            socialMedia = user.socialMedia.mapKeys { (key, _) ->
                mapSocialMediaToString(key)
            },
            subscribedCommunities = user.subscribedCommunities.map { community ->
                CommunityInfoDto(
                    id = community.id,
                    name = community.title,
                    imageUrl = community.imageUrl
                )
            },
            participatingMeetings = user.participatingMeetings.map { meeting ->
                MeetingInfoDto(
                    id = meeting.id,
                    title = meeting.title,
                    imageUrl = meeting.imageUrl,
                    date = ""
                )
            }
        )
    }

    private fun mapSocialMedia(platform: String): SocialMediaType {
        return when (platform.lowercase()) {
            "telegram" -> SocialMediaType.TELEGRAM
            "habr" -> SocialMediaType.HABR
            "github" -> SocialMediaType.GITHUB
            "linkedin" -> SocialMediaType.LINKEDIN
            else -> SocialMediaType.TELEGRAM
        }
    }

    private fun mapSocialMediaToString(platform: SocialMediaType): String {
        return platform.name.lowercase()
    }
}