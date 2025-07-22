package dev.whysoezzy.data.mapper

import dev.whysoezzy.data.remote.dto.CommunityHostDto
import dev.whysoezzy.data.remote.dto.MeetingAddressDto
import dev.whysoezzy.data.remote.dto.MeetingDto
import dev.whysoezzy.data.remote.dto.MeetingInfoDto
import dev.whysoezzy.data.remote.dto.MeetingTagDto
import dev.whysoezzy.data.remote.dto.PersonDto
import dev.whysoezzy.data.remote.dto.PersonHostDto
import dev.whysoezzy.domain.models.CommunityHost
import dev.whysoezzy.domain.models.Meeting
import dev.whysoezzy.domain.models.MeetingAddress
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.Person
import dev.whysoezzy.domain.models.PersonHost
import dev.whysoezzy.domain.models.TagState

class MeetingMapper {
    fun toDomain(dto: MeetingDto): Meeting {
        return Meeting(
            id = dto.id,
            imageUrl = dto.imageUrl,
            title = dto.title,
            description = dto.description,
            time = dto.time,
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
            communityHost = dto.communityHost.let { hostDto ->
                CommunityHost(
                    id = hostDto.id,
                    title = hostDto.title,
                    description = hostDto.description,
                    imageUrl = hostDto.imageUrl,
                    meetingsInfo = hostDto.meetingsInfo.map { infoDto ->
                        MeetingInfo(
                            id = infoDto.id,
                            title = infoDto.title,
                            imageUrl = infoDto.imageUrl,
                            time = infoDto.dateTime,
                            tags = infoDto.tags.map { tag ->
                                MeetingTag(
                                    id = tag.id,
                                    text = tag.text,
                                    state = mapTagState(tag.state)
                                )
                            },
                            address = infoDto.address,
                            meetingStatus = mapMeetingStatus(infoDto.status)
                        )
                    }
                )
            },
            participants = dto.participants.map { personDto ->
                Person(
                    id = personDto.id,
                    name = personDto.name,
                    surname = personDto.surname,
                    avatar = personDto.avatar,
                    bio = personDto.bio
                )
            },
            meetingStatus = mapMeetingStatus(dto.meetingStatus),
            isUserInParticipants = dto.isUserInParticipants,
            date = dto.date,
            capacity = dto.capacity
        )
    }

    fun toDto(meeting: Meeting): MeetingDto {
        return MeetingDto(
            id = meeting.id,
            imageUrl = meeting.imageUrl,
            title = meeting.title,
            description = meeting.description,
            time = meeting.time,
            address = MeetingAddressDto(
                address = meeting.address.address,
                latitude = meeting.address.latitude,
                longitude = meeting.address.longitude
            ),
            tags = meeting.tags.map { tag ->
                MeetingTagDto(
                    id = tag.id,
                    text = tag.text,
                    state = mapTagStateToString(tag.state)
                )
            },
            personHost = PersonHostDto(
                id = meeting.id,
                name = meeting.personHost.name,
                surname = meeting.personHost.surname,
                description = meeting.personHost.description,
                imageUrl = meeting.personHost.imageUrl
            ),
            communityHost = meeting.communityHost.let { host ->
                CommunityHostDto(
                    id = host.id,
                    title = host.title,
                    description = host.description,
                    imageUrl = host.imageUrl,
                    meetingsInfo = host.meetingsInfo.map { info ->
                        MeetingInfoDto(
                            id = info.id,
                            title = info.title,
                            imageUrl = info.imageUrl,
                            dateTime = info.time,
                            tags = info.tags.map { tag ->
                                MeetingTagDto(
                                    id = tag.id,
                                    text = tag.text,
                                    state = mapTagStateToString(tag.state)
                                )
                            },
                            address = info.address,
                            status = mapMeetingStatusToString(info.meetingStatus)
                        )
                    }
                )
            },
            participants = meeting.participants.map { person ->
                PersonDto(
                    id = person.id,
                    name = person.name,
                    surname = person.surname,
                    avatar = person.avatar,
                    bio = person.bio
                )
            },
            meetingStatus = mapMeetingStatusToString(meeting.meetingStatus),
            isUserInParticipants = meeting.isUserInParticipants,
            date = meeting.date,
            capacity = meeting.capacity
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

    private fun mapTagStateToString(state: TagState): String {
        return when (state) {
            TagState.ACTIVE -> "active"
            TagState.INACTIVE -> "inactive"
            TagState.SELECTED -> "selected"
            TagState.DISABLED -> "disabled"
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

    private fun mapMeetingStatusToString(status: MeetingStatus): String {
        return when (status) {
            MeetingStatus.ACTIVE -> "active"
            MeetingStatus.COMPLETED -> "completed"
            MeetingStatus.CANCELLED -> "cancelled"
            MeetingStatus.FULL -> "full"
            MeetingStatus.DRAFT -> "draft"
        }
    }
}