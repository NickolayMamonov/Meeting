package dev.whysoezzy.meet.navigation

import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LegacyAuthCompatibilityTest {
    @Test
    fun `legacy routes retain exact saved destination ids`() {
        assertEquals(
            listOf(
                "auth/phone",
                "auth/code/{phoneNumber}",
                "auth/name/{phone}/{code}",
            ),
            LegacyAuthCompatibility.routes,
        )
        assertEquals(
            navigationDestinationId(MeetRoute.CodeVerification.route),
            MeetRoute.CodeVerification.destinationId,
        )
        assertNotEquals(
            navigationDestinationId("auth/code/{attemptId}"),
            navigationDestinationId("auth/code/{phoneNumber}"),
        )
    }

    @Test
    fun `authenticated state wins over restored pending attempt`() {
        val pending = EmailOtpAttempt(
            attemptId = "attempt-1",
            maskedEmail = "p***@example.com",
            resendAvailableAtEpochMillis = 60_000,
            challengeMayBeActive = true,
            dispatchOutcome = DispatchOutcome.Confirmed,
        )

        assertEquals(
            LegacyAuthRedirectTarget.Main,
            legacyAuthRedirectTarget(
                isLoggedIn = true,
                pendingAttempt = EmailOtpAttemptResult.Found(pending),
            ),
        )
    }

    @Test
    fun `unauthenticated restored pending attempt returns opaque attempt id`() {
        val pending = EmailOtpAttempt(
            attemptId = "attempt-2",
            maskedEmail = "p***@example.com",
            resendAvailableAtEpochMillis = 60_000,
            challengeMayBeActive = true,
            dispatchOutcome = DispatchOutcome.Confirmed,
        )

        assertEquals(
            LegacyAuthRedirectTarget.Code("attempt-2"),
            legacyAuthRedirectTarget(
                isLoggedIn = false,
                pendingAttempt = EmailOtpAttemptResult.Found(pending),
            ),
        )
    }

    @Test
    fun `unauthenticated missing pending attempt returns email entry`() {
        assertEquals(
            LegacyAuthRedirectTarget.Email,
            legacyAuthRedirectTarget(
                isLoggedIn = false,
                pendingAttempt = EmailOtpAttemptResult.MissingOrExpired,
            ),
        )
    }
}
