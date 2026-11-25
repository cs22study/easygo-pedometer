package com.cs22study.easygopedometer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cs22study.easygopedometer.MainActivity
import com.cs22study.easygopedometer.calc.AccelStepDetector
import com.cs22study.easygopedometer.calc.ActiveTimeTracker
import com.cs22study.easygopedometer.calc.SensorCheckpoint
import com.cs22study.easygopedometer.calc.StepDeltaProcessor
import com.cs22study.easygopedometer.data.db.AppDatabase
import com.cs22study.easygopedometer.data.db.HourlyStepEntity
import com.cs22study.easygopedometer.data.db.SensorCheckpointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Continuous foreground pedometer service.
 * Uses hardware TYPE_STEP_COUNTER when available and falls back to 3-axis accelerometer peak detection.
 */
class StepCounterService : Service(), SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val activeTimeTracker = ActiveTimeTracker()
    private val accelDetector = AccelStepDetector()

    private lateinit var sensorManager: SensorManager
    private var usingHardwareStepCounter = false
    private var lastNotificationSteps = -1L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(buildNotification(0L, 0L))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            usingHardwareStepCounter = true
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            usingHardwareStepCounter = false
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        scope.launch {
            restoreTodayActiveTime()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (usingHardwareStepCounter && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val rawCounter = event.values.firstOrNull()?.toLong() ?: return
            val now = LocalDateTime.now()
            val epochSeconds = Instant.now().epochSecond
            scope.launch {
                handleHardwareCounter(rawCounter, now, epochSeconds)
            }
        } else if (!usingHardwareStepCounter && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val detected = accelDetector.onSample(
                x = event.values[0].toDouble(),
                y = event.values[1].toDouble(),
                z = event.values[2].toDouble(),
                timestampMs = System.currentTimeMillis(),
            )
            if (detected > 0) {
                val now = LocalDateTime.now()
                val epochSeconds = Instant.now().epochSecond
                scope.launch {
                    persistDirectDelta(now.toLocalDate(), now.hour, detected.toLong(), epochSeconds)
                }
            }
        }
    }

    private suspend fun restoreTodayActiveTime() {
        val dao = AppDatabase.getInstance(applicationContext).pedometerDao()
        val todayIso = LocalDate.now().toString()
        val rows = dao.getRangeHourly(todayIso, todayIso)
        val totalSteps = rows.sumOf { it.steps }
        val totalSeconds = rows.sumOf { it.activeSeconds }
        activeTimeTracker.restore(totalSeconds)
        updateNotificationIfNeeded(totalSteps, totalSeconds)
    }

    private suspend fun handleHardwareCounter(rawCounter: Long, now: LocalDateTime, epochSeconds: Long) {
        dbMutex.withLock {
            val dao = AppDatabase.getInstance(applicationContext).pedometerDao()
            val prevEntity = dao.getCheckpoint()
            val prevCheckpoint = prevEntity?.let {
                runCatching {
                    SensorCheckpoint(it.lastRawCounter, LocalDateTime.parse(it.lastEventIso))
                }.getOrNull()
            }

            val result = StepDeltaProcessor.process(prevCheckpoint, rawCounter, now)
            dao.upsertCheckpoint(
                SensorCheckpointEntity(
                    id = 1,
                    lastRawCounter = result.nextCheckpoint.lastRawCounter,
                    lastEventIso = result.nextCheckpoint.lastEventTime.toString(),
                )
            )

            val nowIso = OffsetDateTime.now().toString()
            for (delta in result.deltas) {
                if (delta.deltaSteps <= 0L) continue
                val addedActiveSec = activeTimeTracker.recordSteps(delta.deltaSteps, epochSeconds)
                val dateIso = delta.date.toString()
                val existing = dao.getHourly(dateIso, delta.hour)
                val updated = HourlyStepEntity(
                    dateIso = dateIso,
                    hour = delta.hour,
                    steps = (existing?.steps ?: 0L) + delta.deltaSteps,
                    activeSeconds = (existing?.activeSeconds ?: 0L) + addedActiveSec,
                    updatedAtIso = nowIso,
                    isDeleted = false,
                    deletedAtIso = "",
                )
                dao.upsertHourly(updated)
            }

            val todayIso = LocalDate.now().toString()
            val todayRows = dao.getRangeHourly(todayIso, todayIso)
            updateNotificationIfNeeded(todayRows.sumOf { it.steps }, todayRows.sumOf { it.activeSeconds })
        }
    }

    private suspend fun persistDirectDelta(date: LocalDate, hour: Int, deltaSteps: Long, epochSeconds: Long) {
        dbMutex.withLock {
            val dao = AppDatabase.getInstance(applicationContext).pedometerDao()
            val addedActiveSec = activeTimeTracker.recordSteps(deltaSteps, epochSeconds)
            val dateIso = date.toString()
            val existing = dao.getHourly(dateIso, hour)
            dao.upsertHourly(
                HourlyStepEntity(
                    dateIso = dateIso,
                    hour = hour,
                    steps = (existing?.steps ?: 0L) + deltaSteps,
                    activeSeconds = (existing?.activeSeconds ?: 0L) + addedActiveSec,
                    updatedAtIso = OffsetDateTime.now().toString(),
                    isDeleted = false,
                    deletedAtIso = "",
                )
            )
            val todayRows = dao.getRangeHourly(dateIso, dateIso)
            updateNotificationIfNeeded(todayRows.sumOf { it.steps }, todayRows.sumOf { it.activeSeconds })
        }
    }

    private fun updateNotificationIfNeeded(todaySteps: Long, todayActiveSec: Long) {
        if (todaySteps == lastNotificationSteps) return
        lastNotificationSteps = todaySteps
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(todaySteps, todayActiveSec))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "背景持續計步",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "於背景持續累計每日步數與運動時間"
                setShowBadge(false)
            }
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Long, activeSeconds: Long): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val formattedTime = ActiveTimeTracker.formatDuration(activeSeconds)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("EasyGo 想走就走計步器")
            .setContentText("今日累計 %,d 步 · 運動時間 %s".format(steps, formattedTime))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "easygo_pedometer_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
