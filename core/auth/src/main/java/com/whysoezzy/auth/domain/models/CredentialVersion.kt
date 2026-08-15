package com.whysoezzy.auth.domain.models

data class CredentialVersion(
    val epoch: String,
    val revision: Long,
)

data class AuthCredentialState(
    val session: AuthSession,
    val credentialVersion: CredentialVersion,
)
