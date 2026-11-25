package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

class AccelStepDetectorTest {

    @Test
    fun `ignores isolated single shake`() {
        val detector = AccelStepDetector()
        var steps = 0
        steps += detector.onSample(0.0, 9.81, 0.0, 0L)
        steps += detector.onSample(0.0, 14.5, 0.0, 500L)
        steps += detector.onSample(0.0, 9.81, 0.0, 600L)
        assertEquals(0, steps)
    }

    @Test
    fun `detects rhythmic walking after confirmation window`() {
        val detector = AccelStepDetector(peakThresholdMs2 = 1.5, confirmationSteps = 4)
        var total = 0
        // Simulate ~2 Hz walking sine wave at 50 Hz sampling for 4 seconds (8 steps)
        for (i in 0..200) {
            val tMs = i * 20L
            val y = 9.81 + 3.2 * sin(2.0 * Math.PI * 2.0 * (tMs / 1000.0))
            total += detector.onSample(0.0, y, 0.0, tMs)
        }
        assertTrue(total in 6..9, "Expected ~8 steps, got $total")
    }
}
