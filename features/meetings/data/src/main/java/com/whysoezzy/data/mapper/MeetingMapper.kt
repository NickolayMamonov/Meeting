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
    fun toDomain(dto: MeetingDto): Meeting = dto.toDomain()
}