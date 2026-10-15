package com.whysoezzy.common.utils

/**
 * Returns whether a coordinate is a usable geographic location.
 *
 * The provider treats the zero/zero pair as an unset location, but either zero axis is valid.
 */
fun isValidMapCoordinate(
    latitude: Double,
    longitude: Double,
): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
