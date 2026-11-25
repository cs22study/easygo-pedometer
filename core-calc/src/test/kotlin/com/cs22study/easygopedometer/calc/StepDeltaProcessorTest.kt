package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StepDeltaProcessorTest {

    @Test
    fun `first initialization sets baseline with zero deltas`() {
        val now = LocalDateTime.of(2026, 11, 25, 9, 0)
        val result = StepDeltaProcessor.process(null, 12500L, now)

        assertTrue(result.deltas.isEmpty())
        assertFalse(result.rebootDetected)
        assertTrue(result.baselineReanchored)
        assertEquals(12500L, result.nextCheckpoint.lastRawCounter)
        assertEquals(now, result.nextCheckpoint.lastEventTime)
    }

    @Test
    fun `normal increment on same day allocates steps to current hour`() {
        val prev = SensorCheckpoint(1000L, LocalDateTime.of(2026, 11, 25, 9, 15))
        val now = LocalDateTime.of(2026, 11, 25, 10, 5)
        val result = StepDeltaProcessor.process(prev, 1850L, now)

        assertFalse(result.rebootDetected)
        assertFalse(result.baselineReanchored)
        assertEquals(1, result.deltas.size)
        assertEquals(HourlyStepDelta(LocalDate.of(2026, 11, 25), 10, 850L), result.deltas[0])
    }

    @Test
    fun `device reboot resets hardware counter and counts steps since reboot`() {
        val prev = SensorCheckpoint(15000L, LocalDateTime.of(2026, 11, 25, 14, 0))
        val now = LocalDateTime.of(2026, 11, 25, 14, 30)
        val result = StepDeltaProcessor.process(prev, 320L, now)

        assertTrue(result.rebootDetected)
        assertEquals(1, result.deltas.size)
        assertEquals(320L, result.deltas[0].deltaSteps)
        assertEquals(320L, result.nextCheckpoint.lastRawCounter)
    }

    @Test
    fun `midnight rollover splits delta proportionally without losing steps`() {
        val prev = SensorCheckpoint(2000L, LocalDateTime.of(2026, 11, 25, 23, 50))
        val now = LocalDateTime.of(2026, 11, 26, 0, 10)
        val result = StepDeltaProcessor.process(prev, 3000L, now)

        assertEquals(2, result.deltas.size)
        assertEquals(HourlyStepDelta(LocalDate.of(2026, 11, 25), 23, 500L), result.deltas[0])
        assertEquals(HourlyStepDelta(LocalDate.of(2026, 11, 26), 0, 500L), result.deltas[1])
        assertEquals(1000L, result.deltas.sumOf { it.deltaSteps })
    }

    @Test
    fun `rejects Samsung cold-start 0 to 419064 jump and reanchors baseline`() {
        val prev = SensorCheckpoint(0L, LocalDateTime.of(2026, 11, 25, 19, 36, 0))
        val now = LocalDateTime.of(2026, 11, 25, 19, 36, 2)
        val result = StepDeltaProcessor.process(prev, 419064L, now)

        assertTrue(result.deltas.isEmpty())
        assertTrue(result.baselineReanchored)
        assertEquals(419064L, result.nextCheckpoint.lastRawCounter)

        // Subsequent normal walking after reanchor counts properly
        val next = StepDeltaProcessor.process(result.nextCheckpoint, 419089L, now.plusSeconds(15))
        assertEquals(1, next.deltas.size)
        assertEquals(25L, next.deltas[0].deltaSteps)
    }

    @Test
    fun `rejects physically impossible burst exceeding 5 steps per second`() {
        val prev = SensorCheckpoint(5000L, LocalDateTime.of(2026, 11, 25, 19, 0, 0))
        val now = LocalDateTime.of(2026, 11, 25, 19, 0, 5)
        val result = StepDeltaProcessor.process(prev, 55000L, now)

        assertTrue(result.deltas.isEmpty())
        assertTrue(result.baselineReanchored)
        assertEquals(55000L, result.nextCheckpoint.lastRawCounter)
    }
}
