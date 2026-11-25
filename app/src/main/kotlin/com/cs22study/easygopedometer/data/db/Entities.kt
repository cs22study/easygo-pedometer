package com.cs22study.easygopedometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hourly_steps", primaryKeys = ["dateIso", "hour"])
data class HourlyStepEntity(
    val dateIso: String,
    val hour: Int,
    val steps: Long,
    val activeSeconds: Long,
    val updatedAtIso: String,
    val isDeleted: Boolean = false,
    val deletedAtIso: String = "",
)

@Entity(tableName = "sensor_checkpoint")
data class SensorCheckpointEntity(
    @PrimaryKey val id: Int = 1,
    val lastRawCounter: Long,
    val lastEventIso: String,
)
