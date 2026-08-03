package dev.whysoezzy.meet.navigation

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
}
