package com.cs22study.easygopedometer.calc

import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class CalculatedMetrics(
    val steps: Long,
    val activeSeconds: Long,
    val distanceMeters: Long,
    val avgSpeedKmh: Double,
    val caloriesKcal: Double,
    val formattedDuration: String,
)

/**
 * Pure calculation formulas for distance, average speed, and MET-based calorie expenditure.
 */
object MetricsCalculator {

    fun calculate(
        steps: Long,
        activeSeconds: Long,
        profile: UserProfile,
    ): CalculatedMetrics {
        val safeSteps = steps.coerceAtLeast(0L)
        val safeSeconds = activeSeconds.coerceAtLeast(0L)

        val strideMeters = profile.effectiveStrideMeters()
        val distanceMetersExact = safeSteps * strideMeters
        val distanceMeters = distanceMetersExact.roundToLong()

        val avgSpeedKmhRaw = if (safeSeconds > 0L && distanceMetersExact > 0.0) {
            (distanceMetersExact / 1000.0) / (safeSeconds / 3600.0)
        } else {
            0.0
        }
        val avgSpeedKmh = round1Decimal(avgSpeedKmhRaw)

        val caloriesRaw = calculateCalories(
            steps = safeSteps,
            activeSeconds = safeSeconds,
            speedKmh = avgSpeedKmhRaw,
            weightKg = profile.weightKg,
        )
        val caloriesKcal = round1Decimal(caloriesRaw)

        return CalculatedMetrics(
            steps = safeSteps,
            activeSeconds = safeSeconds,
            distanceMeters = distanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            caloriesKcal = caloriesKcal,
            formattedDuration = ActiveTimeTracker.formatDuration(safeSeconds),
        )
    }

    internal fun metForSpeed(speedKmh: Double): Double = when {
        speedKmh <= 0.0 -> 0.0
        speedKmh < 2.5 -> 2.0
        speedKmh < 3.5 -> 2.8
        speedKmh < 4.5 -> 3.1
        speedKmh < 5.5 -> 3.8
        else -> 4.8
    }

    private fun calculateCalories(
        steps: Long,
        activeSeconds: Long,
        speedKmh: Double,
        weightKg: Double,
    ): Double {
        if (steps == 0L) return 0.0
        if (activeSeconds <= 0L) {
            // Conservative step-based fallback before a continuous cadence window completes
            return steps * 0.038 * (weightKg / 70.0)
        }
        val met = metForSpeed(speedKmh)
        val hours = activeSeconds / 3600.0
        return met * weightKg * hours * 1.05
    }

    private fun round1Decimal(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
