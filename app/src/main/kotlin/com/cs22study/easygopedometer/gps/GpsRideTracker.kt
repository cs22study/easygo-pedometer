package com.cs22study.easygopedometer.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.cs22study.easygopedometer.calc.GpsPoint
import com.cs22study.easygopedometer.calc.GpsRideCalculator
import com.cs22study.easygopedometer.calc.GpsRideState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android native LocationManager (GPS_PROVIDER) for offline cycling speedometer tracking.
 * Coordinates remain strictly in memory and are never transmitted over network.
 */
class GpsRideTracker(context: Context) {

    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _rideState = MutableStateFlow(GpsRideState())
    val rideState: StateFlow<GpsRideState> = _rideState.asStateFlow()

    private var weightKg: Double = 68.0
    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val point = GpsPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = if (location.time > 0L) location.time else System.currentTimeMillis(),
                speedMps = if (location.hasSpeed()) location.speed else null,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 15f,
            )
            _rideState.value = GpsRideCalculator.onLocationUpdate(
                current = _rideState.value,
                point = point,
                weightKg = weightKg,
            )
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun isGpsProviderEnabled(): Boolean {
        return runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun startRide(userWeightKg: Double) {
        weightKg = userWeightKg.coerceIn(20.0, 300.0)
        _rideState.value = _rideState.value.copy(isTracking = true, lastFix = null)
        if (!listening) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
                listening = true
            }
        }
    }

    fun pauseRide() {
        _rideState.value = _rideState.value.copy(
            isTracking = false,
            currentSpeedKmh = 0.0,
            lastFix = null,
        )
        stopUpdates()
    }

    fun resetRide() {
        stopUpdates()
        _rideState.value = GpsRideState()
    }

    fun stopUpdates() {
        if (listening) {
            runCatching { locationManager.removeUpdates(listener) }
            listening = false
        }
    }
}
