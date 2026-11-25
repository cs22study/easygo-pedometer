package com.cs22study.easygopedometer.calc

import kotlin.math.sqrt

/**
 * Pure Kotlin 3-axis accelerometer step detector fallback for devices without hardware TYPE_STEP_COUNTER.
 * Uses gravity low-pass subtraction, minimum inter-step interval, and a consecutive-step confirmation window
 * to reject shaking and vehicle vibration.
 */
class AccelStepDetector(
    private val peakThresholdMs2: Double = 1.8,
    private val minStepIntervalMs: Long = 260L,
    private val maxStepIntervalMs: Long = 1800L,
    private val confirmationSteps: Int = 4,
) {
    private var gravity = 9.81
    private var prevDynamic = 0.0
    private var prevPrevDynamic = 0.0
    private var lastPeakTimeMs = 0L
    private var pendingConsecutive = 0

    fun reset() {
        gravity = 9.81
        prevDynamic = 0.0
        prevPrevDynamic = 0.0
        lastPeakTimeMs = 0L
        pendingConsecutive = 0
    }

    /**
     * Feeds one accelerometer sample (x, y, z in m/s^2) at [timestampMs].
     * Returns confirmed step count emitted by this sample (0 during initial noise or confirmation buffer,
     * [confirmationSteps] when the rhythm threshold is first crossed, 1 on subsequent rhythmic steps).
     */
    fun onSample(x: Double, y: Double, z: Double, timestampMs: Long): Int {
        val magnitude = sqrt(x * x + y * y + z * z)
        gravity = 0.85 * gravity + 0.15 * magnitude
        val dynamic = magnitude - gravity

        val isLocalPeak = prevDynamic > prevPrevDynamic && prevDynamic >= dynamic && prevDynamic > peakThresholdMs2
        prevPrevDynamic = prevDynamic
        prevDynamic = dynamic

        if (!isLocalPeak) return 0

        val dt = if (lastPeakTimeMs == 0L) Long.MAX_VALUE else timestampMs - lastPeakTimeMs
        if (dt < minStepIntervalMs) {
            return 0
        }

        if (dt > maxStepIntervalMs) {
            pendingConsecutive = 1
            lastPeakTimeMs = timestampMs
            return 0
        }

        lastPeakTimeMs = timestampMs
        pendingConsecutive++

        return when {
            pendingConsecutive == confirmationSteps -> confirmationSteps
            pendingConsecutive > confirmationSteps -> 1
            else -> 0
        }
    }
}
