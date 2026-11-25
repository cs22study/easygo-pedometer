package com.cs22study.easygopedometer.calc

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class DailyCsvRecord(
    val date: LocalDate,
    val weekday: String,
    val steps: Long,
    val activeSeconds: Long,
    val distanceMeters: Long,
    val caloriesKcal: Double,
    val avgSpeedKmh: Double,
    val strideMeters: Double,
    val updatedAtIso: String,
    val isDeleted: Boolean = false,
    val deletedAtIso: String = "",
)

data class HourlyCsvRecord(
    val date: LocalDate,
    val hour: Int,
    val steps: Long,
    val activeSeconds: Long,
    val updatedAtIso: String,
    val isDeleted: Boolean = false,
    val deletedAtIso: String = "",
)

/**
 * UTF-8 CSV codec with soft-delete support, RFC-4180 quoting, and CSV formula injection protection.
 */
object CsvCodec {

    private const val UTF8_BOM = "\uFEFF"

    private const val DAILY_HEADER =
        "date,weekday,steps,active_seconds,distance_m,calories_kcal,avg_speed_kmh,stride_m,updated_at,is_deleted,deleted_at"

    private const val HOURLY_HEADER =
        "date,hour,steps,active_seconds,updated_at,is_deleted,deleted_at"

    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun encodeDailyRecords(records: List<DailyCsvRecord>, includeBom: Boolean = true): String {
        val sb = StringBuilder()
        if (includeBom) sb.append(UTF8_BOM)
        sb.appendLine(DAILY_HEADER)
        records.sortedBy { it.date }.forEach { r ->
            val cols = listOf(
                r.date.toString(),
                escapeCell(r.weekday),
                r.steps.coerceAtLeast(0L).toString(),
                r.activeSeconds.coerceAtLeast(0L).toString(),
                r.distanceMeters.coerceAtLeast(0L).toString(),
                String.format("%.1f", r.caloriesKcal.coerceAtLeast(0.0)),
                String.format("%.1f", r.avgSpeedKmh.coerceAtLeast(0.0)),
                String.format("%.2f", r.strideMeters.coerceAtLeast(0.0)),
                escapeCell(r.updatedAtIso),
                if (r.isDeleted) "1" else "0",
                escapeCell(r.deletedAtIso),
            )
            sb.appendLine(cols.joinToString(","))
        }
        return sb.toString()
    }

    fun decodeDailyRecords(csvText: String): List<DailyCsvRecord> {
        val lines = normalizeLines(csvText)
        if (lines.isEmpty()) return emptyList()
        require(lines.first().trim() == DAILY_HEADER) { "Invalid daily CSV header" }

        return lines.drop(1).mapIndexed { idx, line ->
            val cols = parseCsvLine(line)
            require(cols.size == 11) { "Invalid column count at data line ${idx + 1}: expected 11, got ${cols.size}" }
            val date = parseLocalDate(cols[0], idx + 1)
            val steps = parseNonNegativeLong(cols[2], "steps", idx + 1)
            val activeSeconds = parseNonNegativeLong(cols[3], "active_seconds", idx + 1)
            val distanceMeters = parseNonNegativeLong(cols[4], "distance_m", idx + 1)
            val caloriesKcal = parseNonNegativeDouble(cols[5], "calories_kcal", idx + 1)
            val avgSpeedKmh = parseNonNegativeDouble(cols[6], "avg_speed_kmh", idx + 1)
            val strideMeters = parseNonNegativeDouble(cols[7], "stride_m", idx + 1)
            val isDeleted = when (cols[9].trim()) {
                "1", "true" -> true
                "0", "false", "" -> false
                else -> throw IllegalArgumentException("Invalid is_deleted flag at line ${idx + 1}")
            }
            DailyCsvRecord(
                date = date,
                weekday = unescapeFormulaGuard(cols[1]),
                steps = steps,
                activeSeconds = activeSeconds,
                distanceMeters = distanceMeters,
                caloriesKcal = caloriesKcal,
                avgSpeedKmh = avgSpeedKmh,
                strideMeters = strideMeters,
                updatedAtIso = unescapeFormulaGuard(cols[8]),
                isDeleted = isDeleted,
                deletedAtIso = unescapeFormulaGuard(cols[10]),
            )
        }
    }

    fun encodeHourlyRecords(records: List<HourlyCsvRecord>, includeBom: Boolean = true): String {
        val sb = StringBuilder()
        if (includeBom) sb.append(UTF8_BOM)
        sb.appendLine(HOURLY_HEADER)
        records.sortedWith(compareBy<HourlyCsvRecord> { it.date }.thenBy { it.hour }).forEach { r ->
            require(r.hour in 0..23) { "Hour must be 0..23" }
            val cols = listOf(
                r.date.toString(),
                r.hour.toString(),
                r.steps.coerceAtLeast(0L).toString(),
                r.activeSeconds.coerceAtLeast(0L).toString(),
                escapeCell(r.updatedAtIso),
                if (r.isDeleted) "1" else "0",
                escapeCell(r.deletedAtIso),
            )
            sb.appendLine(cols.joinToString(","))
        }
        return sb.toString()
    }

    fun decodeHourlyRecords(csvText: String): List<HourlyCsvRecord> {
        val lines = normalizeLines(csvText)
        if (lines.isEmpty()) return emptyList()
        require(lines.first().trim() == HOURLY_HEADER) { "Invalid hourly CSV header" }

        return lines.drop(1).mapIndexed { idx, line ->
            val cols = parseCsvLine(line)
            require(cols.size == 7) { "Invalid column count at data line ${idx + 1}: expected 7, got ${cols.size}" }
            val date = parseLocalDate(cols[0], idx + 1)
            val hour = cols[1].trim().toIntOrNull()
                ?: throw IllegalArgumentException("Invalid hour at line ${idx + 1}")
            require(hour in 0..23) { "Hour out of range (0..23) at line ${idx + 1}" }
            val steps = parseNonNegativeLong(cols[2], "steps", idx + 1)
            val activeSeconds = parseNonNegativeLong(cols[3], "active_seconds", idx + 1)
            val isDeleted = when (cols[5].trim()) {
                "1", "true" -> true
                "0", "false", "" -> false
                else -> throw IllegalArgumentException("Invalid is_deleted flag at line ${idx + 1}")
            }
            HourlyCsvRecord(
                date = date,
                hour = hour,
                steps = steps,
                activeSeconds = activeSeconds,
                updatedAtIso = unescapeFormulaGuard(cols[4]),
                isDeleted = isDeleted,
                deletedAtIso = unescapeFormulaGuard(cols[6]),
            )
        }
    }

    /**
     * Prevents CSV formula injection when opened in Excel / Google Sheets by prefixing dangerous leading chars.
     */
    internal fun escapeCell(raw: String): String {
        val guarded = if (raw.isNotEmpty() && raw[0] in FORMULA_PREFIXES) {
            "'$raw"
        } else {
            raw
        }
        return if (guarded.contains(',') || guarded.contains('"') || guarded.contains('\n')) {
            "\"${guarded.replace("\"", "\"\"")}\""
        } else {
            guarded
        }
    }

    private fun unescapeFormulaGuard(value: String): String {
        return if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_PREFIXES) {
            value.substring(1)
        } else {
            value
        }
    }

    private fun normalizeLines(csvText: String): List<String> {
        val stripped = csvText.removePrefix(UTF8_BOM)
        return stripped.split("\r\n", "\n", "\r")
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        result.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            i++
        }
        require(!inQuotes) { "Unterminated quoted CSV field" }
        result.add(current.toString())
        return result
    }

    private fun parseLocalDate(raw: String, lineNum: Int): LocalDate = try {
        LocalDate.parse(raw.trim())
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date '$raw' at line $lineNum", e)
    }

    private fun parseNonNegativeLong(raw: String, field: String, lineNum: Int): Long {
        val value = raw.trim().toLongOrNull()
            ?: throw IllegalArgumentException("Invalid integer '$raw' for $field at line $lineNum")
        require(value >= 0L) { "$field cannot be negative at line $lineNum" }
        return value
    }

    private fun parseNonNegativeDouble(raw: String, field: String, lineNum: Int): Double {
        val value = raw.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number '$raw' for $field at line $lineNum")
        require(value >= 0.0) { "$field cannot be negative at line $lineNum" }
        return value
    }
}
