package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetricsCalculatorTest {

    @Test
    fun `matches reference screenshot numbers for 4602 steps and 50m54s`() {
        val profile = UserProfile(weightKg = 68.0, customStrideMeters = 0.70)
        val metrics = MetricsCalculator.calculate(
            steps = 4602L,
            activeSeconds = 3054L,
            profile = profile,
        )

        assertEquals(3221L, metrics.distanceMeters)
        assertEquals(3.8, metrics.avgSpeedKmh)
        assertEquals("00:50:54", metrics.formattedDuration)
        assertTrue(metrics.caloriesKcal in 175.0..195.0, "Expected ~180 kcal, got ${metrics.caloriesKcal}")
    }

    @Test
    fun `zero active seconds yields zero speed and safe fallback calories`() {
        val profile = UserProfile(weightKg = 70.0, customStrideMeters = 0.70)
        val metrics = MetricsCalculator.calculate(steps = 100L, activeSeconds = 0L, profile = profile)

        assertEquals(70L, metrics.distanceMeters)
        assertEquals(0.0, metrics.avgSpeedKmh)
        assertEquals("00:00:00", metrics.formattedDuration)
        assertTrue(metrics.caloriesKcal > 0.0)
    }

    @Test
    fun `auto stride estimation uses height and gender when custom stride is null`() {
        val male = UserProfile(gender = Gender.MALE, heightCm = 175.0, customStrideMeters = null)
        val female = UserProfile(gender = Gender.FEMALE, heightCm = 160.0, customStrideMeters = null)
        assertEquals(0.73, male.effectiveStrideMeters())
        assertEquals(0.66, female.effectiveStrideMeters())
    }
}
