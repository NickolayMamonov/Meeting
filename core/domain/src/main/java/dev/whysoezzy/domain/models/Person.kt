package dev.whysoezzy.domain.models

data class Person(
    val id: Long,
    val name: String,
    val surname: String,
    val avatar: String,
    val bio: String?
)
