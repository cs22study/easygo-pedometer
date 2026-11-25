package com.cs22study.easygopedometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PedometerDao {

    @Query("SELECT * FROM hourly_steps WHERE isDeleted = 0 ORDER BY dateIso ASC, hour ASC")
    fun observeAllActive(): Flow<List<HourlyStepEntity>>

    @Query("SELECT DISTINCT dateIso FROM hourly_steps WHERE isDeleted = 1 ORDER BY dateIso DESC")
    fun observeDeletedDates(): Flow<List<String>>

    @Query("SELECT * FROM hourly_steps WHERE dateIso = :dateIso AND isDeleted = 0 ORDER BY hour ASC")
    fun observeDayHourly(dateIso: String): Flow<List<HourlyStepEntity>>

    @Query("SELECT * FROM hourly_steps WHERE dateIso BETWEEN :startDateIso AND :endDateIso AND isDeleted = 0 ORDER BY dateIso ASC, hour ASC")
    suspend fun getRangeHourly(startDateIso: String, endDateIso: String): List<HourlyStepEntity>

    @Query("SELECT * FROM hourly_steps ORDER BY dateIso ASC, hour ASC")
    suspend fun getAllIncludingDeleted(): List<HourlyStepEntity>

    @Query("SELECT * FROM hourly_steps WHERE dateIso = :dateIso AND hour = :hour LIMIT 1")
    suspend fun getHourly(dateIso: String, hour: Int): HourlyStepEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHourly(entity: HourlyStepEntity)

    @Query("UPDATE hourly_steps SET isDeleted = 1, deletedAtIso = :deletedAtIso WHERE dateIso = :dateIso")
    suspend fun softDeleteDay(dateIso: String, deletedAtIso: String)

    @Query("UPDATE hourly_steps SET isDeleted = 1, deletedAtIso = :deletedAtIso WHERE steps > 30000 AND isDeleted = 0")
    suspend fun softDeleteImpossibleSpikes(deletedAtIso: String): Int

    @Query("UPDATE hourly_steps SET isDeleted = 0, deletedAtIso = '' WHERE dateIso = :dateIso")
    suspend fun restoreDay(dateIso: String)

    @Query("SELECT * FROM sensor_checkpoint WHERE id = 1 LIMIT 1")
    suspend fun getCheckpoint(): SensorCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: SensorCheckpointEntity)
}
