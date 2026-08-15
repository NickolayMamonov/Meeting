package com.whysoezzy.common.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A process-local signal only. Durable permission eligibility is owned by the app push store;
 * this bus carries no account, meeting, or notification content.
 */
object MeetingJoinEvents {
    private val mutableEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        replay = 0,
    )

    val events: SharedFlow<Unit> = mutableEvents.asSharedFlow()

    fun emitSuccessfulJoin() {
        mutableEvents.tryEmit(Unit)
    }
}
