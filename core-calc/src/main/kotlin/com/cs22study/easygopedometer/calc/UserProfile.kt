package com.cs22study.easygopedometer.calc

import kotlin.math.roundToInt

enum class Gender {
    MALE,
    FEMALE,
    UNSPECIFIED,
}

enum class DistanceUnit {
    METERS,
    KILOMETERS,
}

/**
 * User physical profile used for stride length, distance, and calorie calculations.
 */
data class UserProfile(
    val age: Int = 35,
    val gender: Gender = Gender.UNSPECIFIED,
    val weightKg: Double = 68.0,
    val heightCm: Double = 168.0,
    val customStrideMeters: Double? = 0.70,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
) {
    init {
        require(age in 1..130) { "Age must be between 1 and 130" }
        require(weightKg in 20.0..300.0) { "Weight must be between 20 and 300 kg" }
        require(heightCm in 80.0..250.0) { "Height must be between 80 and 250 cm" }
        if (customStrideMeters != null) {
            require(customStrideMeters in 0.30..1.50) { "Stride must be between 0.30 and 1.50 m" }
        }
    }

    /**
     * Effective stride length in meters. Uses custom stride if set; otherwise estimates from height and gender.
     */
    fun effectiveStrideMeters(): Double {
        if (customStrideMeters != null) return customStrideMeters
        val factor = when (gender) {
            Gender.MALE -> 0.415
            Gender.FEMALE -> 0.413
            Gender.UNSPECIFIED -> 0.414
        }
        val raw = (heightCm * factor) / 100.0
        return (raw * 100.0).roundToInt() / 100.0
    }
}
