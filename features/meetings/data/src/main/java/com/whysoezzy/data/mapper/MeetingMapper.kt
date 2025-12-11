package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.MeetingDto
import com.whysoezzy.domain.models.CommunityHost
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.PersonHost
import com.whysoezzy.domain.models.TagState

class MeetingMapper {

    fun toDomain(dto: MeetingDto): Meeting {
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
                    state = mapTagState(tagDto.state)
                )
            },
            personHost = PersonHost(
                id = dto.personHost.id,
                name = dto.personHost.name,
                surname = dto.personHost.surname,
                description = dto.personHost.description,
                imageUrl = dto.personHost.imageUrl
            ),
            communityHost = CommunityHost(
                id = dto.communityHost.id,
                title = dto.communityHost.title,
                description = dto.communityHost.description,
                imageUrl = dto.communityHost.imageUrl,
                meetingsInfo = dto.communityHost.meetingsInfo.map { infoDto ->
                    MeetingInfo(
                        id = infoDto.id,
                        title = infoDto.title,
                        imageUrl = infoDto.imageUrl,
                        time = infoDto.time,
                        tags = infoDto.tags.map { tag ->
                            MeetingTag(
                                id = tag.id,
                                text = tag.text,
                                state = mapTagState(tag.state)
                            )
                        },
                        address = infoDto.address,
                        meetingStatus = mapMeetingStatus(infoDto.status),
                    )
                }
            ),
            participants = dto.participants.map { personDto ->
                Person(
                    id = personDto.id,
                    name = personDto.name,
                    surname = personDto.surname,
                    avatar = personDto.avatar,
                    bio = personDto.bio ?: ""
                )
            },
            meetingStatus = mapMeetingStatus(dto.meetingStatus),
            isUserInParticipants = dto.isUserInParticipants,
            capacity = dto.capacity ?: 0
        )
    }

    private fun mapTagState(state: String): TagState {
        return when (state.lowercase().trim()) {
            "active" -> TagState.ACTIVE
            "inactive" -> TagState.INACTIVE
            "selected" -> TagState.SELECTED
            "disabled" -> TagState.DISABLED
            "pressed" -> TagState.SELECTED
            "not_pressed" -> TagState.ACTIVE
            else -> TagState.ACTIVE
        }
    }

    private fun mapMeetingStatus(status: String): MeetingStatus {
        return when (status.lowercase().trim()) {
            "active" -> MeetingStatus.ACTIVE
            "completed" -> MeetingStatus.COMPLETED
            "cancelled" -> MeetingStatus.CANCELLED
            "full" -> MeetingStatus.FULL
            "draft" -> MeetingStatus.DRAFT
            else -> MeetingStatus.ACTIVE
        }
    }
}