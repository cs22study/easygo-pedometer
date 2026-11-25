package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvCodecTest {

    @Test
    fun `daily CSV round-trip preserves soft-deleted records and UTF-8 BOM`() {
        val records = listOf(
            DailyCsvRecord(
                date = LocalDate.of(2026, 11, 24),
                weekday = "週二",
                steps = 8520L,
                activeSeconds = 5100L,
                distanceMeters = 5964L,
                caloriesKcal = 310.5,
                avgSpeedKmh = 4.2,
                strideMeters = 0.70,
                updatedAtIso = "2026-11-24T22:00:00+08:00",
                isDeleted = true,
                deletedAtIso = "2026-11-25T08:00:00+08:00",
            ),
            DailyCsvRecord(
                date = LocalDate.of(2026, 11, 25),
                weekday = "週三",
                steps = 4602L,
                activeSeconds = 3054L,
                distanceMeters = 3221L,
                caloriesKcal = 179.6,
                avgSpeedKmh = 3.8,
                strideMeters = 0.70,
                updatedAtIso = "2026-11-25T15:51:00+08:00",
                isDeleted = false,
                deletedAtIso = "",
            ),
        )

        val encoded = CsvCodec.encodeDailyRecords(records, includeBom = true)
        assertTrue(encoded.startsWith("\uFEFF"))

        val decoded = CsvCodec.decodeDailyRecords(encoded)
        assertEquals(2, decoded.size)
        assertTrue(decoded[0].isDeleted)
        assertEquals("2026-11-25T08:00:00+08:00", decoded[0].deletedAtIso)
        assertFalse(decoded[1].isDeleted)
        assertEquals(4602L, decoded[1].steps)
    }

    @Test
    fun `escapes spreadsheet formula injection prefixes`() {
        assertEquals("'=CMD()", CsvCodec.escapeCell("=CMD()"))
        assertEquals("'+1+1", CsvCodec.escapeCell("+1+1"))
        assertEquals("'-2+3", CsvCodec.escapeCell("-2+3"))
        assertEquals("'@SUM(A1)", CsvCodec.escapeCell("@SUM(A1)"))
    }

    @Test
    fun `hourly CSV round-trip succeeds and rejects invalid hours`() {
        val items = listOf(
            HourlyCsvRecord(LocalDate.of(2026, 11, 25), 8, 1200L, 700L, "2026-11-25T09:00:00+08:00"),
            HourlyCsvRecord(LocalDate.of(2026, 11, 25), 14, 3402L, 2354L, "2026-11-25T15:00:00+08:00"),
        )
        val csv = CsvCodec.encodeHourlyRecords(items)
        val decoded = CsvCodec.decodeHourlyRecords(csv)
        assertEquals(items, decoded)

        val badCsv = "date,hour,steps,active_seconds,updated_at,is_deleted,deleted_at\n2026-11-25,25,100,10,2026-11-25T00:00:00,0,"
        assertThrows(IllegalArgumentException::class.java) {
            CsvCodec.decodeHourlyRecords(badCsv)
        }
    }
}
