package dev.whysoezzy.meet.push

internal object NotificationPermissionPolicy {
    fun markSuccessfulJoin(state: PushStateV1): PushStateV1 =
        PushStateReducer.markPermissionEligible(state)

    fun shouldRequest(state: PushStateV1, apiLevel: Int): Boolean =
        apiLevel >= 33 && state.installPolicy.permission == PermissionState.ELIGIBLE

    fun markRequested(state: PushStateV1): PushStateV1 =
        PushStateReducer.markPermissionRequested(state)
}
