package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingDto
import com.whysoezzy.data.dto.UserInfoDto
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.CommunityHost
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.PersonHost
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.models.TagState

class CommunityMapper {

    fun toDomain(dto: CommunityDto): Community {
        return Community(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            imageUrl = dto.imageUrl,
            subscribersCount = dto.subscribersCount,
            isSubscribed = dto.isSubscribed,
            tags = dto.tags.map { tagDto ->
                Tag(
                    id = tagDto.id,
                    name = tagDto.name
                )
            }
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

    fun userInfoToDomain(dto: UserInfoDto): Person {
        return Person(
            id = dto.id,
            name = dto.name,
            surname = dto.surname,
            avatarUrl = dto.avatarUrl,
            bio = dto.bio,
            role = dto.role
        )
    }

    fun meetingToDomain(dto: MeetingDto): Meeting {
        return Meeting(
            id = dto.id,
            imageUrl = dto.imageUrl,
            title = dto.title,
            description = dto.description,
            time = dto.time,
            date = dto.date,
            address = MeetingAddress(
                address = dto.address.address,
                latitude = dto.address.latitude,
                longitude = dto.address.longitude
            ),
            tags = dto.tags.map { tagDto ->
                MeetingTag(
                    id = tagDto.id,
                    text = tagDto.text,
                    state = TagState.ACTIVE
                )
            },
            personHost = dto.personHost?.let { personHostDto ->
                PersonHost(
                    id = personHostDto.id,
                    name = personHostDto.name,
                    surname = personHostDto.surname,
                    description = personHostDto.description,
                    imageUrl = personHostDto.imageUrl
                )
            },
            communityHost = dto.communityHost?.let { communityHostDto ->
                CommunityHost(
                    id = communityHostDto.id,
                    title = communityHostDto.title,
                    description = communityHostDto.description,
                    imageUrl = communityHostDto.imageUrl,
                    meetingsInfo = communityHostDto.meetingsInfo.map { infoDto ->
                        MeetingInfo(
                            id = infoDto.id,
                            title = infoDto.title,
                            imageUrl = infoDto.imageUrl,
                            time = 0L,
                            tags = emptyList(),
                            address = "",
                            meetingStatus = MeetingStatus.ACTIVE,
                        )
                    }
                )
            },
            participants = dto.participants.map { personDto ->
                Person(
                    id = personDto.id,
                    name = personDto.name,
                    surname = personDto.surname,
                    avatarUrl = personDto.imageUrl ?: "",
                    bio = personDto.bio ?: "",
                    role = personDto.bio?.takeIf { it.isNotBlank() } ?: "Не указано"
                )
            },
            meetingStatus = mapMeetingStatus(dto.meetingStatus),
            isUserInParticipants = dto.isUserInParticipants,
            capacity = dto.capacity ?: 0
        )
    }

    private fun mapMeetingStatus(status: String): MeetingStatus {
        return when (status.uppercase().trim()) {
            "ACTIVE" -> MeetingStatus.ACTIVE
            "COMPLETED" -> MeetingStatus.COMPLETED
            "CANCELLED" -> MeetingStatus.CANCELLED
            "FINISHED" -> MeetingStatus.COMPLETED
            "FULL" -> MeetingStatus.FULL
            "DRAFT" -> MeetingStatus.DRAFT
            else -> MeetingStatus.ACTIVE
        }
    }
}