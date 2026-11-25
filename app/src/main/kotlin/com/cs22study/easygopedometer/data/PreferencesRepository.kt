package com.cs22study.easygopedometer.data

import android.content.Context
import com.cs22study.easygopedometer.calc.DistanceUnit
import com.cs22study.easygopedometer.calc.Gender
import com.cs22study.easygopedometer.calc.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

enum class PowerMode {
    STANDARD_HARDWARE,
    HIGH_SENSITIVITY,
}

data class AppSettings(
    val profile: UserProfile = UserProfile(),
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val powerMode: PowerMode = PowerMode.STANDARD_HARDWARE,
    val healthSyncEnabled: Boolean = false,
    val lastHealthSyncMessage: String = "尚未同步",
)

class PreferencesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("easygo_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val age = prefs.getInt(KEY_AGE, 35).coerceIn(1, 130)
        val gender = runCatching { Gender.valueOf(prefs.getString(KEY_GENDER, Gender.UNSPECIFIED.name)!!) }
            .getOrDefault(Gender.UNSPECIFIED)
        val weight = prefs.getFloat(KEY_WEIGHT, 68f).toDouble().coerceIn(20.0, 300.0)
        val height = prefs.getFloat(KEY_HEIGHT, 168f).toDouble().coerceIn(80.0, 250.0)
        val useAutoStride = prefs.getBoolean(KEY_AUTO_STRIDE, false)
        val customStride = if (useAutoStride) null else prefs.getFloat(KEY_STRIDE, 0.70f).toDouble().coerceIn(0.30, 1.50)
        val unit = runCatching { DistanceUnit.valueOf(prefs.getString(KEY_DISTANCE_UNIT, DistanceUnit.METERS.name)!!) }
            .getOrDefault(DistanceUnit.METERS)
        val weekStart = runCatching { DayOfWeek.valueOf(prefs.getString(KEY_WEEK_START, DayOfWeek.SUNDAY.name)!!) }
            .getOrDefault(DayOfWeek.SUNDAY)
        val powerMode = runCatching { PowerMode.valueOf(prefs.getString(KEY_POWER_MODE, PowerMode.STANDARD_HARDWARE.name)!!) }
            .getOrDefault(PowerMode.STANDARD_HARDWARE)
        val healthSync = prefs.getBoolean(KEY_HEALTH_SYNC, false)
        val lastMsg = prefs.getString(KEY_LAST_SYNC_MSG, "尚未同步") ?: "尚未同步"

        return AppSettings(
            profile = UserProfile(
                age = age,
                gender = gender,
                weightKg = weight,
                heightCm = height,
                customStrideMeters = customStride,
                distanceUnit = unit,
            ),
            weekStart = weekStart,
            powerMode = powerMode,
            healthSyncEnabled = healthSync,
            lastHealthSyncMessage = lastMsg,
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putInt(KEY_AGE, updated.profile.age)
            .putString(KEY_GENDER, updated.profile.gender.name)
            .putFloat(KEY_WEIGHT, updated.profile.weightKg.toFloat())
            .putFloat(KEY_HEIGHT, updated.profile.heightCm.toFloat())
            .putBoolean(KEY_AUTO_STRIDE, updated.profile.customStrideMeters == null)
            .putFloat(KEY_STRIDE, (updated.profile.customStrideMeters ?: updated.profile.effectiveStrideMeters()).toFloat())
            .putString(KEY_DISTANCE_UNIT, updated.profile.distanceUnit.name)
            .putString(KEY_WEEK_START, updated.weekStart.name)
            .putString(KEY_POWER_MODE, updated.powerMode.name)
            .putBoolean(KEY_HEALTH_SYNC, updated.healthSyncEnabled)
            .putString(KEY_LAST_SYNC_MSG, updated.lastHealthSyncMessage)
            .apply()
        _settings.value = updated
    }

    companion object {
        private const val KEY_AGE = "age"
        private const val KEY_GENDER = "gender"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_AUTO_STRIDE = "auto_stride"
        private const val KEY_STRIDE = "stride_m"
        private const val KEY_DISTANCE_UNIT = "distance_unit"
        private const val KEY_WEEK_START = "week_start"
        private const val KEY_POWER_MODE = "power_mode"
        private const val KEY_HEALTH_SYNC = "health_sync"
        private const val KEY_LAST_SYNC_MSG = "last_sync_msg"
    }
}
