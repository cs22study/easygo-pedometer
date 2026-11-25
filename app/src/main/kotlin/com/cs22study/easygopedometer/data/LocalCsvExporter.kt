package com.cs22study.easygopedometer.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.cs22study.easygopedometer.calc.CsvCodec
import com.cs22study.easygopedometer.calc.DailyCsvRecord
import com.cs22study.easygopedometer.calc.DateDisplayFormatter
import com.cs22study.easygopedometer.calc.HourlyCsvRecord
import com.cs22study.easygopedometer.calc.MetricsCalculator
import com.cs22study.easygopedometer.calc.UserProfile
import com.cs22study.easygopedometer.data.db.HourlyStepEntity
import java.io.File
import java.time.LocalDate

data class CsvExportResult(
    val dailyLocation: String,
    val hourlyLocation: String,
    val dailyRows: Int,
)

object LocalCsvExporter {

    private const val DAILY_FILE = "easygo_daily_summary.csv"
    private const val HOURLY_FILE = "easygo_hourly_detail.csv"

    fun exportAll(
        context: Context,
        entities: List<HourlyStepEntity>,
        profile: UserProfile,
    ): CsvExportResult {
        val hourlyRecords = entities.map {
            HourlyCsvRecord(
                date = LocalDate.parse(it.dateIso),
                hour = it.hour,
                steps = it.steps,
                activeSeconds = it.activeSeconds,
                updatedAtIso = it.updatedAtIso,
                isDeleted = it.isDeleted,
                deletedAtIso = it.deletedAtIso,
            )
        }

        val dailyRecords = hourlyRecords
            .groupBy { it.date }
            .map { (date, rows) ->
                val activeRows = rows.filter { !it.isDeleted }
                val allDeleted = activeRows.isEmpty() && rows.all { it.isDeleted }
                val targetRows = if (allDeleted) rows else activeRows
                val totalSteps = targetRows.sumOf { it.steps }
                val totalSeconds = targetRows.sumOf { it.activeSeconds }
                val metrics = MetricsCalculator.calculate(totalSteps, totalSeconds, profile)
                DailyCsvRecord(
                    date = date,
                    weekday = DateDisplayFormatter.weekdayLabel(date.dayOfWeek),
                    steps = metrics.steps,
                    activeSeconds = metrics.activeSeconds,
                    distanceMeters = metrics.distanceMeters,
                    caloriesKcal = metrics.caloriesKcal,
                    avgSpeedKmh = metrics.avgSpeedKmh,
                    strideMeters = profile.effectiveStrideMeters(),
                    updatedAtIso = targetRows.maxOfOrNull { it.updatedAtIso } ?: "",
                    isDeleted = allDeleted,
                    deletedAtIso = if (allDeleted) rows.maxOfOrNull { it.deletedAtIso } ?: "" else "",
                )
            }

        val dailyCsv = CsvCodec.encodeDailyRecords(dailyRecords, includeBom = true)
        val hourlyCsv = CsvCodec.encodeHourlyRecords(hourlyRecords, includeBom = true)

        val dailyLoc = writeCsvFile(context, DAILY_FILE, dailyCsv)
        val hourlyLoc = writeCsvFile(context, HOURLY_FILE, hourlyCsv)

        return CsvExportResult(
            dailyLocation = dailyLoc,
            hourlyLocation = hourlyLoc,
            dailyRows = dailyRecords.size,
        )
    }

    private fun writeCsvFile(context: Context, fileName: String, content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/EasyGo-Pedometer"
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            resolver.delete(MediaStore.Files.getContentUri("external"), selection, arrayOf(fileName, "%EasyGo-Pedometer%"))

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                return "Documents/EasyGo-Pedometer/$fileName"
            }
        }

        // Android 7.0 - 9 fallback: app-scoped external Documents folder (zero extra permissions)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "EasyGo-Pedometer")
        dir.mkdirs()
        val temp = File(dir, "$fileName.tmp")
        val target = File(dir, fileName)
        temp.writeBytes(bytes)
        if (target.exists()) target.delete()
        temp.renameTo(target)
        return target.absolutePath
    }
}
