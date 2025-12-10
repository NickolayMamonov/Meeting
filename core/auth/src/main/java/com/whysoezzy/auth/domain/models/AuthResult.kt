package com.whysoezzy.auth.domain.models

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long
)
