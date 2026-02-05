package com.whysoezzy.domain.models

data class Person(
    val id: Long,
    val name: String,
    val surname: String,
    val avatarUrl: String,
    val bio: String,
    val role: String
)