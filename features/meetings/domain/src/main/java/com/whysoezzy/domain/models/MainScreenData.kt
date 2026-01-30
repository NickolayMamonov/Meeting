package com.whysoezzy.domain.models

data class MainScreenData(
    val heroMeetings: List<Meeting>,
    val popularMeetings: List<Meeting>,
    val allMeetings: List<Meeting>,
    val categories: List<MeetingTag>,
    val communities: List<CommunityInfo>
)