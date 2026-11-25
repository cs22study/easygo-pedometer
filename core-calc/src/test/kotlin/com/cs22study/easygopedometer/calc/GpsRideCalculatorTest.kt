package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpsRideCalculatorTest {

    @Test
    fun `rejects poor GPS accuracy and stationary drift below 1_5 kmh`() {
        var state = GpsRideState(isTracking = true)
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0330, 121.5654, timestampMillis = 1_000L, speedMps = 0.2f, accuracyMeters = 8f),
        )
        // Poor accuracy update (>35m) ignored for distance
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0340, 121.5654, timestampMillis = 5_000L, speedMps = 5.0f, accuracyMeters = 60f),
        )
        assertEquals(0L, state.distanceMeters)

        // Stationary jitter (< 1.5 km/h) snaps to 0.0 and does not accumulate distance
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.033001, 121.565401, timestampMillis = 6_000L, speedMps = 0.2f, accuracyMeters = 6f),
        )
        assertEquals(0.0, state.currentSpeedKmh)
        assertEquals(0L, state.distanceMeters)
    }

    @Test
    fun `accumulates cycling distance, speed, and calories cleanly`() {
        var state = GpsRideState(isTracking = true)
        state = GpsRideCalculator.onLocationUpdate(
            state,
            GpsPoint(25.0330, 121.5654, timestampMillis = 0L, speedMps = 5.5f, accuracyMeters = 5f),
        )
        // ~111 m north in 20 seconds => ~20 km/h
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
}
