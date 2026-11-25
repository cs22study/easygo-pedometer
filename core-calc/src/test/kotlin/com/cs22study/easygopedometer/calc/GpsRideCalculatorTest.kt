package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpsRideCalculatorTest {

    @Test
    fun `rejects poor GPS accuracy and stationary drift below 1_5 kmh`() {
        var state = GpsRideState(isTracking = true, speedMode = SpeedMode.BIKE)
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0330, 121.5654, timestampMillis = 1_000L, speedMps = 0.2f, accuracyMeters = 8f),
        )
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0340, 121.5654, timestampMillis = 5_000L, speedMps = 5.0f, accuracyMeters = 60f),
        )
        assertEquals(0L, state.distanceMeters)

        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.033001, 121.565401, timestampMillis = 6_000L, speedMps = 0.2f, accuracyMeters = 6f),
        )
        assertEquals(0.0, state.currentSpeedKmh)
        assertEquals(0L, state.distanceMeters)
    }

    @Test
    fun `accumulates cycling distance, speed, and calories cleanly`() {
        var state = GpsRideState(isTracking = true, speedMode = SpeedMode.BIKE)
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0330, 121.5654, timestampMillis = 0L, speedMps = 5.5f, accuracyMeters = 5f),
        )
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0340, 121.5654, timestampMillis = 20_000L, speedMps = 5.5f, accuracyMeters = 5f),
            weightKg = 70.0,
        )

        assertEquals(19.8, state.currentSpeedKmh)
        assertEquals(19.8, state.maxSpeedKmh)
        assertTrue(state.distanceMeters in 105L..118L, "Expected ~111m, got ${state.distanceMeters}")
        assertEquals(20L, state.elapsedSeconds)
        assertTrue(state.caloriesKcal > 0.0)
    }

    @Test
    fun `HSR mode supports 300 kmh without calories inflation`() {
        var state = GpsRideState(isTracking = true, speedMode = SpeedMode.HSR)
        // 83.33 m/s == 300.0 km/h
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0000, 121.5000, timestampMillis = 0L, speedMps = 83.333f, accuracyMeters = 45f),
        )
        // ~833 m north in 10 seconds
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0075, 121.5000, timestampMillis = 10_000L, speedMps = 83.333f, accuracyMeters = 45f),
            weightKg = 70.0,
        )

        assertEquals(300.0, state.currentSpeedKmh)
        assertEquals(300.0, state.maxSpeedKmh)
        assertTrue(state.distanceMeters in 800L..860L, "Expected ~833m, got ${state.distanceMeters}")
        assertEquals(0.0, state.caloriesKcal)
    }
}
