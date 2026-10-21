package dev.whysoezzy.uikit.components.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPreviewStateTest {
    @Test
    fun `eligibility requires a valid in-preview coordinate token and GLES3`() {
        assertTrue(mapPreviewEligibility(55.0, 37.0, hasPublicToken = true, supportsGles3 = true).showMap)
        assertFalse(mapPreviewEligibility(55.0, 37.0, hasPublicToken = false, supportsGles3 = true).showMap)
        assertFalse(mapPreviewEligibility(55.0, 37.0, hasPublicToken = true, supportsGles3 = false).showMap)
        assertFalse(mapPreviewEligibility(85.0512, 37.0, hasPublicToken = true, supportsGles3 = true).showMap)
        assertFalse(mapPreviewEligibility(0.0, 0.0, hasPublicToken = true, supportsGles3 = true).showMap)
        assertTrue(mapPreviewEligibility(0.0, 37.0, hasPublicToken = false, supportsGles3 = false).coordinateValid)
    }

    @Test
    fun `snapshot viewport requires two hundred by ninety six density independent pixels`() {
        assertTrue(isSnapshotViewportEligible(widthPx = 400, heightPx = 192, pixelRatio = 2f))
        assertFalse(isSnapshotViewportEligible(widthPx = 399, heightPx = 192, pixelRatio = 2f))
        assertFalse(isSnapshotViewportEligible(widthPx = 400, heightPx = 191, pixelRatio = 2f))
        assertFalse(isSnapshotViewportEligible(widthPx = 0, heightPx = 192, pixelRatio = 2f))
        assertFalse(isSnapshotViewportEligible(widthPx = 400, heightPx = 192, pixelRatio = 0f))
    }

    @Test
    fun `feedback link is generic and excludes meeting metadata`() {
        val url = mapFeedbackUrl()

        assertEquals("https://apps.mapbox.com/feedback/", url)
        assertFalse(url.contains("55.7"))
        assertFalse(url.contains("37.6"))
        assertFalse(url.contains("Тверская"))
        assertFalse(url.contains("12345"))
    }

    @Test
    fun `blank address uses the exact generic map action label`() {
        assertEquals("Открыть в картах", mapActionContentDescription("", coordinateValid = true))
        assertEquals("Открыть в картах", mapActionContentDescription(" \t ", coordinateValid = true))
        assertEquals("Открыть в картах", mapActionContentDescription("улица", coordinateValid = false))
        assertEquals("Открыть в картах: улица", mapActionContentDescription("улица", coordinateValid = true))
    }

    @Test
    fun `deadline pauses while backgrounded and terminates at fifteen foreground seconds`() {
        val deadline = ForegroundRenderDeadline()
        deadline.resume(1_000L)
        assertEquals(10_000L, deadline.remainingMillis(6_000L))
        deadline.pause(6_000L)

        assertEquals(10_000L, deadline.remainingMillis(100_000L))
        deadline.resume(100_000L)
        assertEquals(1L, deadline.remainingMillis(109_999L))
        assertEquals(0L, deadline.remainingMillis(110_000L))
    }
}
