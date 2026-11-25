package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveTimeTrackerTest {

    @Test
    fun `isolated single step does not accumulate active time`() {
        val tracker = ActiveTimeTracker(maxActiveGapSeconds = 10L, minBurstSteps = 3L)
        assertEquals(0L, tracker.recordSteps(1L, 100L))
        assertEquals(0L, tracker.recordSteps(1L, 160L)) // huge idle gap
        assertEquals(0L, tracker.activeSeconds)
    }

    @Test
    fun `continuous walking burst commits active seconds accurately`() {
        val tracker = ActiveTimeTracker(maxActiveGapSeconds = 10L, minBurstSteps = 3L)
        tracker.recordSteps(1L, 100L)
        tracker.recordSteps(1L, 101L)
        val added = tracker.recordSteps(2L, 103L)
        assertEquals(3L, added)
        assertEquals(3L, tracker.activeSeconds)

        val next = tracker.recordSteps(4L, 106L)
        assertEquals(3L, next)
        assertEquals(6L, tracker.activeSeconds)
    }

    @Test
    fun `formats duration as HH MM SS`() {
        assertEquals("00:50:54", ActiveTimeTracker.formatDuration(3054L))
        assertEquals("21:49:22", ActiveTimeTracker.formatDuration(21 * 3600L + 49 * 60L + 22L))
    }
}
