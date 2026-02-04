package com.whysoezzy.domain.models

data class SearchData(
    val meetings: List<Meeting>,
    val communities: List<CommunityInfo>
)