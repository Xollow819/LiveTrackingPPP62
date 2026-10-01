package com.ppp62.livetracking.util

import org.junit.Assert.*
import org.junit.Test

class LocationUtilsTest {
    @Test fun samePointIsZero() = assertEquals(0.0, LocationUtils.distanceMeters(-6.9, 107.6, -6.9, 107.6), 0.01)
    @Test fun nearbyPointIsInside75Meters() = assertTrue(LocationUtils.distanceMeters(-6.9147, 107.6098, -6.9149, 107.6099) < 75)
    @Test fun farPointIsOutside75Meters() = assertTrue(LocationUtils.distanceMeters(-6.9147, 107.6098, -6.9160, 107.6110) > 75)
    @Test fun updateOlderThan90SecondsIsStale() = assertTrue(LocationUtils.isStale(1_000, 91_001))
    @Test fun exactly90SecondsIsNotStale() = assertFalse(LocationUtils.isStale(1_000, 91_000))
}
