package com.whysoezzy.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PushInstallationTest {
    @Test
    fun `installation id accepts only canonical lowercase UUID`() {
        assertEquals(
            UUID,
            PushInstallationId(UUID).value,
        )

        for (invalid in listOf(UUID.uppercase(), "{$UUID}", UUID.dropLast(1), "not-a-uuid")) {
            val result = runCatching { PushInstallationId(invalid) }
            assertFalse("Expected invalid installation ID: $invalid", result.isSuccess)
        }
    }

    @Test
    fun `fid is opaque nonblank and redacted from string rendering`() {
        val fid = PushInstallationFid("opaque-value")

        assertEquals("opaque-value", fid.value)
        assertFalse(fid.toString().contains(fid.value))
        assertFalse(runCatching { PushInstallationFid("   ") }.isSuccess)
    }

    private companion object {
        const val UUID = "550e8400-e29b-41d4-a716-446655440000"
    }
}
