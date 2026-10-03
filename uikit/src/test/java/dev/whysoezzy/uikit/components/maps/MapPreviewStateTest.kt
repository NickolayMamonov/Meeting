package dev.whysoezzy.uikit.components.maps

import com.mapbox.maps.RenderModeType
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
        assertFalse(mapPreviewEligibility(0.0, 37.0, hasPublicToken = false, supportsGles3 = false).showMap)
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
    fun `full frame before map loaded is latched and reveals without another frame`() {
        val readiness = MapPreviewReadiness()

        readiness.onStyleLoaded()
        readiness.onPinInstalled()
        readiness.onFrameFinished(RenderModeType.FULL)
        assertEquals(MapPreviewStatus.LOADING, readiness.status)

        readiness.onMapLoaded()
        assertEquals(MapPreviewStatus.READY, readiness.status)
    }

    @Test
    fun `partial frame cannot reveal the map and timeout is terminal`() {
        val readiness = MapPreviewReadiness()
        readiness.onStyleLoaded()
        readiness.onPinInstalled()
        readiness.onMapLoaded()
        readiness.onFrameFinished(RenderModeType.PARTIAL)
        assertEquals(MapPreviewStatus.LOADING, readiness.status)

        readiness.onDeadline()
        readiness.onFrameFinished(RenderModeType.FULL)
        assertEquals(MapPreviewStatus.UNAVAILABLE, readiness.status)
    }

    @Test
    fun `incomplete loading can time out and late success cannot revive it`() {
        val readiness = MapPreviewReadiness()
        readiness.onStyleLoaded()
        readiness.onPinInstalled()
        readiness.onDeadline()

        readiness.onMapLoaded()
        readiness.onFrameFinished(RenderModeType.FULL)
        assertEquals(MapPreviewStatus.UNAVAILABLE, readiness.status)
    }

    @Test
    fun `failure is terminal before or after readiness`() {
        val loading = MapPreviewReadiness()
        loading.onFailure()
        loading.onStyleLoaded()
        loading.onPinInstalled()
        loading.onMapLoaded()
        loading.onFrameFinished(RenderModeType.FULL)
        assertEquals(MapPreviewStatus.UNAVAILABLE, loading.status)

        val ready = MapPreviewReadiness()
        ready.onStyleLoaded()
        ready.onPinInstalled()
        ready.onMapLoaded()
        ready.onFrameFinished(RenderModeType.FULL)
        ready.onFailure()
        assertEquals(MapPreviewStatus.UNAVAILABLE, ready.status)
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
        deadline.pause(110_000L)
        assertEquals(0L, deadline.remainingMillis(120_000L))
    }

    @Test
    fun `disposal invalidates callbacks from the old attempt`() {
        val readiness = MapPreviewReadiness()
        readiness.dispose()
        readiness.onStyleLoaded()
        readiness.onPinInstalled()
        readiness.onMapLoaded()
        readiness.onFrameFinished(RenderModeType.FULL)
        assertEquals(MapPreviewStatus.UNAVAILABLE, readiness.status)
    }
}
