package com.cs22study.easygopedometer.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.cs22study.easygopedometer.data.db.HourlyStepEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class HealthSyncAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    NOT_INSTALLED_OR_UNAVAILABLE,
    UNSUPPORTED_OS,
}

data class HealthSyncResult(
    val success: Boolean,
    val message: String,
)

/**
 * Bridges EasyGo-Pedometer steps & active duration into Android Health Connect,
 * which Google Fit uses on modern Android devices (replacing the deprecated Fit API sunsetting end of 2026).
 * Safely degrades on Android 7.0-8.1 (API 24-27) without crashing.
 */
object HealthConnectBridge {

    val requiredPermissions: Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    } else {
        emptySet()
    }

    fun checkAvailability(context: Context): HealthSyncAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return HealthSyncAvailability.UNSUPPORTED_OS
        }
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthSyncAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthSyncAvailability.PROVIDER_UPDATE_REQUIRED
            else -> HealthSyncAvailability.NOT_INSTALLED_OR_UNAVAILABLE
        }
    }

    fun availabilityDescription(status: HealthSyncAvailability): String = when (status) {
        HealthSyncAvailability.AVAILABLE -> "Health Connect 已就緒（可同步至 Google Fit）"
        HealthSyncAvailability.PROVIDER_UPDATE_REQUIRED -> "需至 Play 商店安裝或更新 Health Connect"
        HealthSyncAvailability.NOT_INSTALLED_OR_UNAVAILABLE -> "裝置未安裝 Health Connect 服務"
        HealthSyncAvailability.UNSUPPORTED_OS -> "Android 9 以上支援 Health Connect 同步；Android 7–8 已自動使用本機計步與 CSV 備份"
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        if (checkAvailability(context) != HealthSyncAvailability.AVAILABLE) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    suspend fun syncHourlyRecords(context: Context, entities: List<HourlyStepEntity>): HealthSyncResult {
        val status = checkAvailability(context)
        if (status != HealthSyncAvailability.AVAILABLE) {
            return HealthSyncResult(false, availabilityDescription(status))
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(requiredPermissions)) {
            return HealthSyncResult(false, "請先授權 Health Connect 步數與運動寫入權限")
        }

        val activeEntities = entities.filter { !it.isDeleted && it.steps in 1L..30_000L }
        if (activeEntities.isEmpty()) {
            return HealthSyncResult(true, "目前尚無可同步之有效步數紀錄")
        }

        val zone = ZoneId.systemDefault()
        val phoneDevice = Device(type = Device.TYPE_PHONE)
        val records = mutableListOf<androidx.health.connect.client.records.Record>()

        for (item in activeEntities) {
            val date = LocalDate.parse(item.dateIso)
            val startZdt = ZonedDateTime.of(LocalDateTime.of(date.year, date.month, date.dayOfMonth, item.hour, 0), zone)
            val endZdt = startZdt.plusHours(1)
            val recordId = "easygo-steps-${item.dateIso}-h${item.hour}"
            records.add(
                StepsRecord(
                    count = item.steps,
                    startTime = startZdt.toInstant(),
                    endTime = endZdt.toInstant(),
                    startZoneOffset = startZdt.offset,
                    endZoneOffset = endZdt.offset,
                    metadata = Metadata.autoRecorded(device = phoneDevice, clientRecordId = recordId),
                )
            )
            if (item.activeSeconds >= 60L) {
                val activeEnd = startZdt.toInstant().plusSeconds(item.activeSeconds.coerceAtMost(3600L))
                records.add(
                    ExerciseSessionRecord(
                        startTime = startZdt.toInstant(),
                        endTime = activeEnd,
                        startZoneOffset = startZdt.offset,
                        endZoneOffset = startZdt.offset,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                        title = "EasyGo 步行紀錄",
                        metadata = Metadata.autoRecorded(
                            device = phoneDevice,
                            clientRecordId = "easygo-walk-${item.dateIso}-h${item.hour}",
                        ),
                    )
                )
            }
        }

        return runCatching {
            client.insertRecords(records)
            val todayTotal = readTodayStepsInternal(client)
            val suffix = if (todayTotal != null) "（今日總計 %,d 步）".format(todayTotal) else ""
            HealthSyncResult(true, "已同步 ${activeEntities.size} 筆時段紀錄至 Health Connect$suffix")
        }.getOrElse { ex ->
            HealthSyncResult(false, "同步失敗：${ex.localizedMessage ?: "未知錯誤"}")
        }
    }

    suspend fun readTodaySteps(context: Context): Long? {
        if (!hasAllPermissions(context)) return null
        val client = HealthConnectClient.getOrCreate(context)
        return readTodayStepsInternal(client)
    }

    suspend fun readTodayExerciseSeconds(context: Context): Long {
        if (!hasAllPermissions(context)) return 0L
        val client = HealthConnectClient.getOrCreate(context)
        return runCatching {
            val zone = ZoneId.systemDefault()
            val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
            val now = java.time.Instant.now()
            val resp = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                )
            )
            resp[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.seconds ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun readTodayStepsInternal(client: HealthConnectClient): Long? {
        return runCatching {
            val zone = ZoneId.systemDefault()
            val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
            val now = java.time.Instant.now()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                )
            )
            response[StepsRecord.COUNT_TOTAL]
        }.getOrNull()
    }

    fun isGoogleFitInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("com.google.android.apps.fitness", 0)
            true
        }.getOrDefault(false)
    }

    fun openGoogleFitOrStore(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.fitness")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } else {
            val store = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.fitness")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(store) }
        }
    }
}
