package com.whysoezzy.auth.domain.models

/**
 * The durable authenticated-session state machine.
 *
 * A missing encrypted session is represented explicitly as [Stage.LoggedOut]. Callers must
 * resolve navigation from this value instead of deriving a stage from the current destination.
 */
data class AuthSession(
    val userId: Long?,
    val stage: Stage,
) {
    enum class Stage {
        LoggedOut,
        NeedsName,
        Welcome,
        Ready,
    }

    companion object {
        val LoggedOut = AuthSession(userId = null, stage = Stage.LoggedOut)
    }
}
