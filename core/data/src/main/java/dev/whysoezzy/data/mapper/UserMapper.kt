//package dev.whysoezzy.data.mapper
//
//import dev.whysoezzy.data.remote.dto.CommunityInfoDto
//import dev.whysoezzy.data.remote.dto.MeetingInfoDto
//import dev.whysoezzy.data.remote.dto.MeetingTagDto
//import dev.whysoezzy.data.remote.dto.UserDto
//import dev.whysoezzy.domain.models.CommunityInfo
//import dev.whysoezzy.domain.models.MeetingInfo
//import dev.whysoezzy.domain.models.MeetingStatus
//import dev.whysoezzy.domain.models.MeetingTag
//import dev.whysoezzy.domain.models.TagState
//import dev.whysoezzy.domain.models.User
//
//class UserMapper {
//    fun toDomain(dto: UserDto): User {
//        return User(
//            id = dto.id,
//            name = dto.name,
//            surname = dto.surname,
//            email = dto.email,
//            imageUrl = dto.avatar,
//            phoneNumber = dto.phone,
//            description = dto.bio,
//            interests = dto.interests.map { tagDto ->
//                MeetingTag(
//                    id = tagDto.id,
//                    text = tagDto.text,
//                    state = mapTagState(tagDto.state)
//                )
//            },
//            myCommunities = dto.subscribedCommunities.map {
//                toCommunityInfo(it)
//            },
//            myMeetings = dto.participatingMeetings.map {
//                toMeetingInfo(it)
//            },
//            city = dto.city,
//            socialMedias = dto.socialMedia
//        )
//    }
//
//    fun toDto(user: User): UserDto {
//        return UserDto(
//            id = user.id,
//            name = user.name,
//            surname = user.surname,
//            email = user.email,
//            avatar = user.imageUrl,
//            phone = user.phoneNumber,
//            bio = user.description,
//            city = user.city,
//            interests = user.interests.map { tag ->
//                dev.whysoezzy.data.remote.dto.TagDto(
//                    id = tag.id,
//                    text = tag.text,
//                    state = mapTagStateToString(tag.state)
//                )
//            },
//            subscribedCommunities = user.myCommunities.map {
//                fromCommunityInfo(it)
//            },
//            participatingMeetings = user.myMeetings.map {
//                fromMeetingInfo(it)
//            },
//            socialMedia = user.socialMedias
//
//        )
//    }
//
//    private fun toCommunityInfo(dto: CommunityInfoDto): CommunityInfo {
//        return CommunityInfo(
//            id = dto.id.toLong(),
//            title = dto.title,
//            imageUrl = dto.imageUrl,
//            tags = dto.tags.map {
//                MeetingTag(
//                    id = it.id,
//                    text = it.text,
//                    state = mapTagState(it.state)
//                )
//            }
//        )
//    }
//
//    private fun toMeetingInfo(dto: MeetingInfoDto): MeetingInfo {
//        return MeetingInfo(
//            id = dto.id,
//            title = dto.title,
//            address = dto.address,
//            imageUrl = dto.imageUrl,
//            tags = dto.tags.map {
//                MeetingTag(
//                    id = it.id,
//                    text = it.text,
//                    state = mapTagState(it.state)
//                )
//            },
//            time = dto.dateTime,
//            meetingStatus = parseMeetingStatus(dto.status)
//        )
//    }
//
//    private fun fromCommunityInfo(info: CommunityInfo): CommunityInfoDto {
//        return CommunityInfoDto(
//            id = info.id,
//            title = info.title,
//            imageUrl = info.imageUrl,
//            tags = info.tags.map {
//                MeetingTagDto(
//                    id = it.id,
//                    text = it.text,
//                    state = mapTagStateToString(it.state)
//                )
//            }
//        )
//    }
//
//    private fun fromMeetingInfo(info: MeetingInfo): MeetingInfoDto {
//        return MeetingInfoDto(
//            id = info.id,
//            title = info.title,
//            imageUrl = info.imageUrl,
//            dateTime = info.time,
//            tags = info.tags.map {
//                MeetingTagDto(
//                    id = it.id,
//                    text = it.text,
//                    state = mapTagStateToString(it.state)
//                )
//            },
//            address = info.address,
//            status = mapMeetingStatusToString(info.meetingStatus),
//        )
//    }
//
//    private fun parseMeetingStatus(status: String): MeetingStatus {
//        return when (status.lowercase()) {
//            "active" -> MeetingStatus.ACTIVE
//            "completed" -> MeetingStatus.COMPLETED
//            "cancelled" -> MeetingStatus.CANCELLED
//            "full" -> MeetingStatus.FULL
//            "draft" -> MeetingStatus.DRAFT
//            else -> MeetingStatus.ACTIVE
//        }
//    }
//
//    private fun mapTagState(state: String): TagState {
//        return when (state.lowercase().trim()) {
//            "active" -> TagState.ACTIVE
//            "inactive" -> TagState.INACTIVE
//            "selected" -> TagState.SELECTED
//            "disabled" -> TagState.DISABLED
//            "pressed" -> TagState.SELECTED
//            "not_pressed" -> TagState.ACTIVE
//            else -> TagState.ACTIVE
//        }
//    }
//
//    private fun mapTagStateToString(state: TagState): String {
//        return when (state) {
//            TagState.ACTIVE -> "active"
//            TagState.INACTIVE -> "inactive"
//            TagState.SELECTED -> "selected"
//            TagState.DISABLED -> "disabled"
//        }
//    }
//
//    private fun mapMeetingStatus(status: String): MeetingStatus {
//        return when (status.lowercase().trim()) {
//            "active" -> MeetingStatus.ACTIVE
//            "completed" -> MeetingStatus.COMPLETED
//            "cancelled" -> MeetingStatus.CANCELLED
//            "full" -> MeetingStatus.FULL
//            "draft" -> MeetingStatus.DRAFT
//            else -> MeetingStatus.ACTIVE
//        }
//    }
//
//    private fun mapMeetingStatusToString(status: MeetingStatus): String {
//        return when (status) {
//            MeetingStatus.ACTIVE -> "active"
//            MeetingStatus.COMPLETED -> "completed"
//            MeetingStatus.CANCELLED -> "cancelled"
//            MeetingStatus.FULL -> "full"
//            MeetingStatus.DRAFT -> "draft"
//        }
//    }
//}
//
//
