package com.whysoezzy.common.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A process-local signal only. Durable permission eligibility is owned by the app push store;
 * this bus carries no account, meeting, or notification content.
 */
object MeetingJoinEvents {
    private val mutableEvents = MutableStateFlow<Long?>(null)

    val events: StateFlow<Long?> = mutableEvents.asStateFlow()

    fun emitSuccessfulJoin() {
        mutableEvents.update { (it ?: -1L) + 1L }
    }
}
