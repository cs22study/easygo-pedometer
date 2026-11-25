package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DateDisplayFormatterTest {

    @Test
    fun `formats explicit day header with year month day and weekday`() {
        val date = LocalDate.of(2026, 11, 25)
        assertEquals("2026年11月25日 週三", DateDisplayFormatter.formatDayHeader(date))
    }

    @Test
    fun `formats week range starting Sunday matching reference screenshot`() {
        val date = LocalDate.of(2026, 11, 23)
        assertEquals("11月22日 - 11月28日", DateDisplayFormatter.formatWeekHeader(date, DayOfWeek.SUNDAY))
    }

    @Test
    fun `formats month and year headers`() {
        assertEquals("2026年11月", DateDisplayFormatter.formatMonthHeader(YearMonth.of(2026, 11)))
        assertEquals("2026年", DateDisplayFormatter.formatYearHeader(2026))
    }
}
