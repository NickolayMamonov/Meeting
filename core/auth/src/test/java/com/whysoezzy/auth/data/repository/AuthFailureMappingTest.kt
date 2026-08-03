package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.domain.models.AuthFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthFailureMappingTest {
    private fun map(endpoint: AuthEndpoint, status: Int, code: String) =
        mapAuthFailure(endpoint, ApiErrorMetadata(status, code))

    @Test
    fun `business codes win over status fallback`() {
        assertEquals(AuthFailure.DeliveryUnavailable, map(AuthEndpoint.Send, 400, "X-013-OTP-PROVIDER-UNAVAILABLE"))
        assertEquals(AuthFailure.ActivationUnavailable, map(AuthEndpoint.Send, 400, "B-056-OTP-ACTIVATION-UNAVAILABLE"))
        assertEquals(AuthFailure.RateLimited, map(AuthEndpoint.Send, 200, "OTP-RATE-LIMITED"))
        assertEquals(AuthFailure.InvalidCode, map(AuthEndpoint.Verify, 500, "OTP-INVALID"))
        assertEquals(AuthFailure.InvalidOrExpiredCode, map(AuthEndpoint.Verify, 200, "OTP-EXPIRED"))
    }
}
