package com.cs22study.easygopedometer.calc

/**
 * Tracks active exercise duration (in seconds) from step bursts using a cadence-window filter.
 * Ignores isolated single-step false triggers and overly long idle gaps (e.g. sitting/standing).
 */
class ActiveTimeTracker(
    private val maxActiveGapSeconds: Long = 10L,
    private val minBurstSteps: Long = 3L,
) {
    private var lastStepEpochSeconds: Long? = null
    private var pendingSteps: Long = 0L
    private var pendingSeconds: Long = 0L

    var activeSeconds: Long = 0L
        private set

    fun restore(initialActiveSeconds: Long) {
        require(initialActiveSeconds >= 0L) { "Initial active seconds cannot be negative" }
        activeSeconds = initialActiveSeconds
        lastStepEpochSeconds = null
        pendingSteps = 0L
        pendingSeconds = 0L
    }

    /**
     * Records [deltaSteps] observed at [epochSeconds]. Returns the incremental active seconds added.
     */
    fun recordSteps(deltaSteps: Long, epochSeconds: Long): Long {
        if (deltaSteps <= 0L) return 0L

        val prevTime = lastStepEpochSeconds
        if (prevTime == null || epochSeconds < prevTime) {
            lastStepEpochSeconds = epochSeconds
            pendingSteps = deltaSteps
            pendingSeconds = if (deltaSteps >= minBurstSteps) estimateBurstSeconds(deltaSteps) else 0L
            return commitIfBurstReady()
        }

        val gap = epochSeconds - prevTime
        lastStepEpochSeconds = epochSeconds

        return if (gap <= maxActiveGapSeconds) {
            val effectiveGap = if (gap == 0L) estimateBurstSeconds(deltaSteps) else gap
            pendingSteps += deltaSteps
            pendingSeconds += effectiveGap
            commitIfBurstReady()
        } else {
            // New walking session after an idle pause
            pendingSteps = deltaSteps
            pendingSeconds = if (deltaSteps >= minBurstSteps) estimateBurstSeconds(deltaSteps) else 0L
            commitIfBurstReady()
        }
    }

    private fun estimateBurstSeconds(steps: Long): Long {
        // Average walking cadence ~1.5 steps/sec; conservatively attribute 1s per 2 steps, min 1s.
        return (steps / 2L).coerceAtLeast(1L)
    }

    private fun commitIfBurstReady(): Long {
        if (pendingSteps < minBurstSteps) return 0L
        val added = pendingSeconds
        activeSeconds += added
        pendingSeconds = 0L
        return added
    }

    companion object {
        fun formatDuration(totalSeconds: Long): String {
            val safe = totalSeconds.coerceAtLeast(0L)
            val hours = safe / 3600L
            val minutes = (safe % 3600L) / 60L
            val seconds = safe % 60L
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
