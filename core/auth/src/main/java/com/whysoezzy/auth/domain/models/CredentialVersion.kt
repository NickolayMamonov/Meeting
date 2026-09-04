package com.whysoezzy.auth.domain.models

data class CredentialVersion(
    val epoch: String,
    val revision: Long,
)

data class AuthCredentialState(
    val session: AuthSession,
    val credentialVersion: CredentialVersion,
)

data class AuthCredentialIdentity(
    val userId: Long,
    val stage: AuthSession.Stage,
    val credentialVersion: CredentialVersion,
    val refreshToken: String,
)

data class AuthCredentialSnapshot(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val stage: AuthSession.Stage,
    val credentialVersion: CredentialVersion,
) {
    val identity: AuthCredentialIdentity
        get() = AuthCredentialIdentity(userId, stage, credentialVersion, refreshToken)
}

data class AuthOperationPermit(
    val generation: Long,
    val identity: AuthCredentialIdentity?,
)

data class OwnerSaveReservation(
    val generation: Long,
    val reservationId: Long,
    internal val clearPermit: AuthOperationPermit,
)

data class ClearReservation(
    val generation: Long,
    val reservationId: Long,
    internal val identity: AuthCredentialIdentity?,
)

sealed interface AuthCredentialRead {
    data class Present(
        val snapshot: AuthCredentialSnapshot,
        val permit: AuthOperationPermit,
    ) : AuthCredentialRead

    data class Missing(
        val permit: AuthOperationPermit,
    ) : AuthCredentialRead

    data object Stale : AuthCredentialRead
}

sealed interface AuthSaveResult {
    data object Persisted : AuthSaveResult
    data object StaleSkipped : AuthSaveResult
}

sealed interface AuthClearResult {
    data object Cleared : AuthClearResult
    data object StaleSkipped : AuthClearResult
}

data class PersistedTokenPair(
    val accessToken: String,
    val refreshToken: String,
)

sealed interface AuthRefreshSaveResult {
    data class Persisted(val pair: PersistedTokenPair) : AuthRefreshSaveResult
    data object StaleSkipped : AuthRefreshSaveResult
    data class Failed(val error: Throwable) : AuthRefreshSaveResult
}

sealed interface RefreshOutcome {
    data class Refreshed(val pair: PersistedTokenPair) : RefreshOutcome
    data class Missing(val clearPermit: AuthOperationPermit) : RefreshOutcome
    data class Unauthorized(val clearPermit: AuthOperationPermit) : RefreshOutcome
    data class TransientFailure(val error: Throwable) : RefreshOutcome
    data object StaleSkipped : RefreshOutcome
}
