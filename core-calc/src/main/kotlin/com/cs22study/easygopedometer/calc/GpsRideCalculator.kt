package com.cs22study.easygopedometer.calc

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpeedMode(
    val label: String,
    val maxScaleKmh: Float,
    val maxPlausibleKmh: Double,
    val maxAcceptableAccuracyM: Float,
    val trackCalories: Boolean,
) {
    BIKE(
        label = "單車",
        maxScaleKmh = 60f,
        maxPlausibleKmh = 120.0,
        maxAcceptableAccuracyM = 35f,
        trackCalories = true,
    ),
    VEHICLE(
        label = "車",
        maxScaleKmh = 160f,
        maxPlausibleKmh = 230.0,
        maxAcceptableAccuracyM = 65f,
        trackCalories = false,
    ),
    HSR(
        label = "高鐵",
        maxScaleKmh = 400f,
        maxPlausibleKmh = 450.0,
        maxAcceptableAccuracyM = 65f,
        trackCalories = false,
    ),
}

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val speedMps: Float? = null,
    val accuracyMeters: Float = 10f,
)

data class GpsRideState(
    val isTracking: Boolean = false,
    val speedMode: SpeedMode = SpeedMode.BIKE,
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val distanceMeters: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val caloriesKcal: Double = 0.0,
    val lastAccuracyMeters: Float? = null,
    val lastFix: GpsPoint? = null,
)

/**
 * Pure Kotlin GPS speedometer calculator supporting Bike (60 km/h), Vehicle (160 km/h),
 * and High-Speed Rail (400 km/h), with Haversine fallback and anti-drift filtering.
 */
object GpsRideCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val MIN_MOVING_SPEED_KMH = 1.5

    /**
     * Returns 5 evenly spaced dial scale integers for the given [mode] (0%, 25%, 50%, 75%, 100%).
     */
    fun gaugeTickValues(mode: SpeedMode): List<Int> {
        val max = mode.maxScaleKmh.toInt()
        return listOf(0, max / 4, max / 2, (max * 3) / 4, max)
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun cyclingMetForSpeed(speedKmh: Double): Double = when {
        speedKmh < MIN_MOVING_SPEED_KMH -> 0.0
        speedKmh < 16.0 -> 4.0
        speedKmh < 19.0 -> 6.8
        speedKmh < 22.0 -> 8.0
        else -> 10.0
    }

    fun onLocationUpdate(
        current: GpsRideState,
        point: GpsPoint,
        weightKg: Double = 68.0,
    ): GpsRideState {
        val mode = current.speedMode
        if (!current.isTracking) {
            return current.copy(lastAccuracyMeters = point.accuracyMeters)
        }
        if (point.accuracyMeters > mode.maxAcceptableAccuracyM) {
            return current.copy(lastAccuracyMeters = point.accuracyMeters)
        }

        val prev = current.lastFix
        if (prev == null) {
            val rawSpeedKmh = (point.speedMps?.toDouble()?.times(3.6) ?: 0.0)
                .let { if (it < MIN_MOVING_SPEED_KMH || it > mode.maxPlausibleKmh) 0.0 else it }
            return current.copy(
                currentSpeedKmh = round1(rawSpeedKmh),
                maxSpeedKmh = maxOf(current.maxSpeedKmh, round1(rawSpeedKmh)),
                lastAccuracyMeters = point.accuracyMeters,
                lastFix = point,
            )
        }

        val dtMillis = (point.timestampMillis - prev.timestampMillis).coerceAtLeast(0L)
        if (dtMillis == 0L) {
            return current.copy(lastAccuracyMeters = point.accuracyMeters)
        }

        val segmentDist = haversineMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
        val derivedSpeedKmh = (segmentDist / (dtMillis / 1000.0)) * 3.6
        val sensorSpeedKmh = point.speedMps?.toDouble()?.times(3.6)

        val candidateSpeed = sensorSpeedKmh ?: derivedSpeedKmh
        val cleanSpeed = when {
            candidateSpeed < MIN_MOVING_SPEED_KMH -> 0.0
            candidateSpeed > mode.maxPlausibleKmh -> current.currentSpeedKmh
            else -> candidateSpeed
        }

        val addedDist = if (cleanSpeed >= MIN_MOVING_SPEED_KMH && derivedSpeedKmh <= mode.maxPlausibleKmh) {
            segmentDist.roundToLong()
        } else {
            0L
        }

        val newDistance = current.distanceMeters + addedDist
        val newElapsed = current.elapsedSeconds + (dtMillis / 1000L)
        val avgSpeed = if (newElapsed > 0L && newDistance > 0L) {
            (newDistance / 1000.0) / (newElapsed / 3600.0)
        } else {
            0.0
        }

        val addedKcal = if (mode.trackCalories) {
            val dtHours = dtMillis / 3_600_000.0
            cyclingMetForSpeed(cleanSpeed) * weightKg * dtHours * 1.05
        } else {
            0.0
        }
        val newCalories = current.caloriesKcal + addedKcal

        return current.copy(
            currentSpeedKmh = round1(cleanSpeed),
            maxSpeedKmh = maxOf(current.maxSpeedKmh, round1(cleanSpeed)),
            avgSpeedKmh = round1(avgSpeed),
            distanceMeters = newDistance,
            elapsedSeconds = newElapsed,
            caloriesKcal = round1(newCalories),
            lastAccuracyMeters = point.accuracyMeters,
            lastFix = point,
        )
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
}
