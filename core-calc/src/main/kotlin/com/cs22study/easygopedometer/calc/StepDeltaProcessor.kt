package com.cs22study.easygopedometer.calc

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
)

/**
 * Processes cumulative hardware step counter readings (which reset to 0 on device reboot)
 * into non-negative hourly step deltas, splitting across midnight boundaries if needed.
 */
object StepDeltaProcessor {

    /**
     * Computes step deltas between [previous] checkpoint and [currentRawCounter] observed at [currentTime].
     *
     * When [previous] is null (first initialization), records the baseline without inventing past steps.
     * When [currentRawCounter] < [previous.lastRawCounter], assumes device reboot and treats [currentRawCounter]
     * as steps taken since reboot.
     */
    fun process(
        previous: SensorCheckpoint?,
        currentRawCounter: Long,
        currentTime: LocalDateTime,
    ): StepProcessResult {
        require(currentRawCounter >= 0L) { "Raw counter cannot be negative" }

        if (previous == null) {
            return StepProcessResult(
                deltas = emptyList(),
                nextCheckpoint = SensorCheckpoint(currentRawCounter, currentTime),
                rebootDetected = false,
            )
        }

        val effectiveTime = if (currentTime.isBefore(previous.lastEventTime)) {
            previous.lastEventTime
        } else {
            currentTime
        }

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

    /**
     * Proportionally allocates steps across day boundaries when a device wakes up after midnight.
     * Ensures the sum of all allocated buckets equals [totalDelta] exactly.
     */
    private fun splitAcrossMidnight(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        totalDelta: Long,
    ): List<HourlyStepDelta> {
        val totalSeconds = ChronoUnit.SECONDS.between(startTime, endTime)
        if (totalSeconds <= 0L) {
            return listOf(HourlyStepDelta(endTime.toLocalDate(), endTime.hour, totalDelta))
        }

        // Split proportionally between the previous day's last active hour and the new day's current hour.
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
