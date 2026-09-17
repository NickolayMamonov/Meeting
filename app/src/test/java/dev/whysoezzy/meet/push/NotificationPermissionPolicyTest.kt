package dev.whysoezzy.meet.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `first successful join becomes eligible and requests on api 33`() {
        val eligible = NotificationPermissionPolicy.markSuccessfulJoin(PushStateV1())

        assertEquals(PermissionState.ELIGIBLE, eligible.installPolicy.permission)
        assertTrue(NotificationPermissionPolicy.shouldRequest(eligible, 33))
        assertEquals(
            PermissionState.REQUESTED,
            NotificationPermissionPolicy.markRequested(eligible).installPolicy.permission,
        )
    }

    @Test
    fun `denial and repeated joins never request again`() {
        val requested = PushStateV1(
            installPolicy = InstallPolicyState(PermissionState.REQUESTED),
        )

        assertFalse(NotificationPermissionPolicy.shouldRequest(requested, 33))
        assertFalse(
            NotificationPermissionPolicy.shouldRequest(
                NotificationPermissionPolicy.markSuccessfulJoin(requested),
                33,
            ),
        )
    }

    @Test
    fun `lifecycle resume can consume retained eligibility once`() {
        val eligible = NotificationPermissionPolicy.markSuccessfulJoin(PushStateV1())

        assertTrue(NotificationPermissionPolicy.shouldRequest(eligible, 34))
        val requested = NotificationPermissionPolicy.markRequested(eligible)
        assertFalse(NotificationPermissionPolicy.shouldRequest(requested, 34))
    }

    @Test
    fun `concurrent successful joins are idempotent`() = runTest {
        val state = MutableStateFlow(PushStateV1())
        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) {
                    state.update(NotificationPermissionPolicy::markSuccessfulJoin)
                }
            }
        }

        assertEquals(PermissionState.ELIGIBLE, state.value.installPolicy.permission)
    }
}
