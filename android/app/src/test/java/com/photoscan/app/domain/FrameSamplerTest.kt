package com.photoscan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameSamplerTest {
    @Test
    fun threeFpsOverOneSecondGivesThreeFrames() {
        // 0, 333333, 666667 microseconds; 1,000,000 is excluded.
        assertEquals(
            listOf(0L, 333_333L, 666_667L),
            FrameSampler.timestampsUs(durationUs = 1_000_000L, fps = 3.0),
        )
    }

    @Test
    fun maxFramesCapsTheOutput() {
        assertEquals(
            listOf(0L, 500_000L),
            FrameSampler.timestampsUs(durationUs = 10_000_000L, fps = 2.0, maxFrames = 2),
        )
    }

    @Test
    fun zeroDurationGivesNoFrames() {
        assertEquals(emptyList<Long>(), FrameSampler.timestampsUs(0L, 3.0))
    }

    @Test
    fun nonPositiveFpsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameSampler.timestampsUs(1_000_000L, 0.0)
        }
    }
}
