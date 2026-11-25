package com.cs22study.easygopedometer.calc

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Persisted checkpoint of the hardware TYPE_STEP_COUNTER sensor.
 */
data class SensorCheckpoint(
    val lastRawCounter: Long,
    val lastEventTime: LocalDateTime,
)

/**
 * Incremental step delta allocated to a specific (date, hour) bucket.
 */
data class HourlyStepDelta(
    val date: LocalDate,
    val hour: Int,
    val deltaSteps: Long,
) {
    init {
        require(hour in 0..23) { "Hour must be 0..23" }
        require(deltaSteps >= 0) { "Delta steps cannot be negative" }
    }
}

data class StepProcessResult(
    val deltas: List<HourlyStepDelta>,
    val nextCheckpoint: SensorCheckpoint,
    val rebootDetected: Boolean,
    val baselineReanchored: Boolean = false,
)

/**
 * Processes cumulative hardware step counter readings (which reset to 0 on device reboot)
 * into non-negative hourly step deltas, splitting across midnight boundaries if needed.
 *
 * Includes physical plausibility and cold-start `0 -> lifetime_steps` guards so sensor-hub
 * resyncs (e.g. Samsung Galaxy devices emitting 0 before sending 400k+ boot steps) never pollute daily stats.
 */
object StepDeltaProcessor {

    private const val MAX_STEPS_PER_SECOND = 5L
    private const val BURST_ALLOWANCE_STEPS = 250L

    /**
     * Computes step deltas between [previous] checkpoint and [currentRawCounter] observed at [currentTime].
     */
    fun process(
        previous: SensorCheckpoint?,
        currentRawCounter: Long,
        currentTime: LocalDateTime,
    ): StepProcessResult {
        require(currentRawCounter >= 0L) { "Raw counter cannot be negative" }

        // Do not treat an initial 0 reading as a committed baseline if sensor hub hasn't warmed up yet,
        // or establish baseline directly when non-zero.
        if (previous == null) {
            return StepProcessResult(
                deltas = emptyList(),
                nextCheckpoint = SensorCheckpoint(currentRawCounter, currentTime),
                rebootDetected = false,
                baselineReanchored = true,
            )
        }

        val effectiveTime = if (currentTime.isBefore(previous.lastEventTime)) {
            previous.lastEventTime
        } else {
            currentTime
        }

        val elapsedSeconds = ChronoUnit.SECONDS.between(previous.lastEventTime, effectiveTime).coerceAtLeast(1L)
        val rebootDetected = currentRawCounter < previous.lastRawCounter
        val totalDelta = if (rebootDetected) {
            currentRawCounter
        } else {
            currentRawCounter - previous.lastRawCounter
        }

        if (totalDelta == 0L) {
            return StepProcessResult(
                deltas = emptyList(),
                nextCheckpoint = SensorCheckpoint(currentRawCounter, effectiveTime),
                rebootDetected = rebootDetected,
            )
        }

        // Guard against OEM sensor cold-start (`0 -> 419064` in seconds) or impossible jumps (> 5 steps/sec sustained).
        val maxPlausibleDelta = max(BURST_ALLOWANCE_STEPS, elapsedSeconds * MAX_STEPS_PER_SECOND + BURST_ALLOWANCE_STEPS)
        val coldStartZeroJump = previous.lastRawCounter == 0L && currentRawCounter > BURST_ALLOWANCE_STEPS
        if (coldStartZeroJump || totalDelta > maxPlausibleDelta) {
            return StepProcessResult(
                deltas = emptyList(),
                nextCheckpoint = SensorCheckpoint(currentRawCounter, effectiveTime),
                rebootDetected = rebootDetected,
                baselineReanchored = true,
            )
        }

        val prevDate = previous.lastEventTime.toLocalDate()
        val currDate = effectiveTime.toLocalDate()

        val deltas = if (prevDate == currDate) {
            listOf(HourlyStepDelta(currDate, effectiveTime.hour, totalDelta))
        } else {
            splitAcrossMidnight(
                startTime = previous.lastEventTime,
                endTime = effectiveTime,
                totalDelta = totalDelta,
            )
        }

        return StepProcessResult(
            deltas = deltas,
            nextCheckpoint = SensorCheckpoint(currentRawCounter, effectiveTime),
            rebootDetected = rebootDetected,
        )
    }

    private fun splitAcrossMidnight(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        totalDelta: Long,
    ): List<HourlyStepDelta> {
        val totalSeconds = ChronoUnit.SECONDS.between(startTime, endTime)
        if (totalSeconds <= 0L) {
            return listOf(HourlyStepDelta(endTime.toLocalDate(), endTime.hour, totalDelta))
        }

        val firstMidnight = startTime.toLocalDate().plusDays(1).atStartOfDay()
        val secondsBeforeMidnight = ChronoUnit.SECONDS.between(startTime, firstMidnight).coerceIn(0L, totalSeconds)
        val beforeSteps = (totalDelta * secondsBeforeMidnight) / totalSeconds
        val afterSteps = totalDelta - beforeSteps

        val result = mutableListOf<HourlyStepDelta>()
        if (beforeSteps > 0L) {
            result.add(HourlyStepDelta(startTime.toLocalDate(), startTime.hour, beforeSteps))
        }
        if (afterSteps > 0L) {
            result.add(HourlyStepDelta(endTime.toLocalDate(), endTime.hour, afterSteps))
        }
        return result
    }
}
