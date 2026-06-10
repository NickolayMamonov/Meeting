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

fun MeetingDto.toDomain(): Meeting =
    Meeting(
        id = id,
        imageUrl = imageUrl,
        title = title,
        description = description,
        time = time,
        date = date,
        address =
            MeetingAddress(
                address = address.address,
                latitude = address.latitude,
                longitude = address.longitude,
            ),
        tags =
            tags.map { tagDto ->
                MeetingTag(
                    id = tagDto.id,
                    text = tagDto.text,
                    state = TagState.ACTIVE,
                )
            },
        personHost =
            personHost?.let { personHostDto ->
                PersonHost(
                    id = personHostDto.id,
                    name = personHostDto.name,
                    surname = personHostDto.surname,
                    description = personHostDto.description,
                    imageUrl = personHostDto.imageUrl,
                )
            },
        communityHost =
            communityHost?.let { communityHostDto ->
                CommunityHost(
                    id = communityHostDto.id,
                    title = communityHostDto.title,
                    description = communityHostDto.description,
                    imageUrl = communityHostDto.imageUrl,
                    meetingsInfo =
                        communityHostDto.meetingsInfo.map { infoDto ->
                            MeetingInfo(
                                id = infoDto.id,
                                title = infoDto.title,
                                imageUrl = infoDto.imageUrl,
                                time = 0L,
                                tags = emptyList(),
                                address = "",
                                meetingStatus = MeetingStatus.ACTIVE,
                            )
                        },
                )
            },
        participants =
            participants.map { personDto ->
                Person(
                    id = personDto.id,
                    name = personDto.name,
                    surname = personDto.surname,
                    avatarUrl = personDto.imageUrl ?: "",
                    bio = personDto.bio ?: "",
                    role = personDto.role?.takeIf { it.isNotBlank() } ?: "Не указано",
                )
            },
        meetingStatus = meetingStatus.toMeetingStatus(),
        isUserInParticipants = isUserInParticipants,
        capacity = capacity ?: 0,
        source = source,
        externalUrl = externalUrl,
        isOnline = isOnline,
    )

fun String.toMeetingStatus(): MeetingStatus =
    when (uppercase().trim()) {
        "ACTIVE" -> MeetingStatus.ACTIVE
        "COMPLETED", "FINISHED" -> MeetingStatus.COMPLETED
        "CANCELLED" -> MeetingStatus.CANCELLED
        "FULL" -> MeetingStatus.FULL
        "DRAFT" -> MeetingStatus.DRAFT
        else -> MeetingStatus.ACTIVE
    }
