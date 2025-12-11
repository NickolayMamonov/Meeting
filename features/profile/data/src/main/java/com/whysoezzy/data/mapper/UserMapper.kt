package com.whysoezzy.data.mapper

import com.whysoezzy.common.utils.DateUtils
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.TagDto
import com.whysoezzy.data.dto.UserDto
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.SocialMedia
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.models.User
import java.time.LocalDateTime
import kotlin.collections.mapKeys

class UserMapper {
    fun toDomain(dto: UserDto): User {
        return User(
            id = dto.id,
            name = dto.name,
            surname = dto.surname,
            email = dto.email,
            city = dto.city,
            avatar = dto.avatar,
            phone = dto.phone,
            bio = dto.bio,
            interests = dto.interests.map { tagDto ->
                Tag(
                    id = tagDto.id,
                    name = tagDto.name
                )
            },
            socialMedia = dto.socialMedia.mapKeys { (key, _) ->
                mapSocialMedia(key)
            },
            subscribedCommunities = dto.subscribedCommunities.map { communityDto ->
                CommunityInfo(
                    id = communityDto.id,
                    name = communityDto.name,
                    imageUrl = communityDto.imageUrl
                )
            },
            participatingMeetings = dto.participatingMeetings.map { meetingDto ->

                MeetingInfo(
                    id = meetingDto.id,
                    title = meetingDto.name,
                    imageUrl = meetingDto.imageUrl,
                    dateTime = meetingDto.dateTime
                )
            }
        )
    }

    fun toDto(user: User): UserDto {
        return UserDto(
            id = user.id,
            name = user.name,
            surname = user.surname,
            email = user.email,
            city = user.city,
            avatar = user.avatar,
            phone = user.phone,
            bio = user.bio,
            interests = user.interests.map { tag ->
                TagDto(
                    id = tag.id,
                    name = tag.name
                )
            },
            socialMedia = user.socialMedia.mapKeys { (key, _) ->
                mapSocialMediaToString(key)
            },
            subscribedCommunities = user.subscribedCommunities.map { community ->
                CommunityInfoDto(
                    id = community.id,
                    name = community.name,
                    imageUrl = community.imageUrl
                )
            },
            participatingMeetings = user.participatingMeetings.map { meeting ->
                MeetingInfoDto(
                    id = meeting.id,
                    name = meeting.title,
                    imageUrl = meeting.imageUrl,
                    dateTime = meeting.dateTime
                )
            }
        )
    }

    private fun mapSocialMedia(platform: String): SocialMedia {
        return when (platform.uppercase()) {
            "TELEGRAM" -> SocialMedia.TELEGRAM
            "HABR" -> SocialMedia.HABR
            "GITHUB" -> SocialMedia.GITHUB
            "LINKEDIN" -> SocialMedia.LINKEDIN
            else -> SocialMedia.TELEGRAM
        }
    }

    private fun mapSocialMediaToString(platform: SocialMedia): String {
        return platform.name
    }
}