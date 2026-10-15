package com.whysoezzy.common.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoordinateTest {
    @Test
    fun `accepts finite coordinates at geographic bounds`() {
        assertTrue(isValidMapCoordinate(-90.0, -180.0))
        assertTrue(isValidMapCoordinate(90.0, 180.0))
        assertTrue(isValidMapCoordinate(0.0, 1.0))
        assertTrue(isValidMapCoordinate(1.0, 0.0))
    }

    @Test
    fun `rejects unset zero pair including signed zero`() {
        assertFalse(isValidMapCoordinate(0.0, 0.0))
        assertFalse(isValidMapCoordinate(-0.0, 0.0))
        assertFalse(isValidMapCoordinate(0.0, -0.0))
        assertFalse(isValidMapCoordinate(-0.0, -0.0))
    }

    @Test
    fun `rejects non finite values`() {
        assertFalse(isValidMapCoordinate(Double.NaN, 1.0))
        assertFalse(isValidMapCoordinate(1.0, Double.NaN))
        assertFalse(isValidMapCoordinate(Double.POSITIVE_INFINITY, 1.0))
        assertFalse(isValidMapCoordinate(1.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `rejects values beyond either geographic bound`() {
        assertFalse(isValidMapCoordinate(Math.nextDown(-90.0), 1.0))
        assertFalse(isValidMapCoordinate(Math.nextUp(90.0), 1.0))
        assertFalse(isValidMapCoordinate(1.0, Math.nextDown(-180.0)))
        assertFalse(isValidMapCoordinate(1.0, Math.nextUp(180.0)))
    }
}
