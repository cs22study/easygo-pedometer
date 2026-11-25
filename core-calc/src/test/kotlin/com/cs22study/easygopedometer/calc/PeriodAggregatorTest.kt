package com.cs22study.easygopedometer.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class PeriodAggregatorTest {

    private val profile = UserProfile(weightKg = 68.0, customStrideMeters = 0.70)

    @Test
    fun `aggregateDay builds cumulative hourly curve and ignores soft-deleted records`() {
        val today = LocalDate.of(2026, 11, 25)
        val records = listOf(
            HourlyCsvRecord(today, 8, 500L, 300L, "2026-11-25T08:30:00"),
            HourlyCsvRecord(today, 10, 1500L, 900L, "2026-11-25T10:30:00"),
            HourlyCsvRecord(today, 14, 9999L, 999L, "2026-11-25T14:30:00", isDeleted = true),
        )

        val day = PeriodAggregator.aggregateDay(today, records, profile, maxHourToPlot = 16)
        assertEquals("2026年11月25日 週三", day.headerText)
        assertEquals(2000L, day.metrics.steps)
        assertEquals(1200L, day.metrics.activeSeconds)
        assertEquals(17, day.hourlyCurve.size)
        assertEquals(0L, day.hourlyCurve[7].cumulativeSteps)
        assertEquals(500L, day.hourlyCurve[8].cumulativeSteps)
        assertEquals(2000L, day.hourlyCurve[10].cumulativeSteps)
        assertEquals(2000L, day.hourlyCurve[16].cumulativeSteps)
    }

    @Test
    fun `aggregateWeek produces 7 daily bars and sums metrics`() {
        val wed = LocalDate.of(2026, 11, 25)
        val records = listOf(
            HourlyCsvRecord(LocalDate.of(2026, 11, 22), 9, 1000L, 600L, "t"),
            HourlyCsvRecord(LocalDate.of(2026, 11, 23), 9, 4602L, 3054L, "t"),
            HourlyCsvRecord(LocalDate.of(2026, 11, 29), 9, 5000L, 3000L, "t"), // next week
        )

        val week = PeriodAggregator.aggregateWeek(wed, records, profile, DayOfWeek.SUNDAY)
        assertEquals("11月22日 - 11月28日", week.headerText)
        assertEquals(5602L, week.metrics.steps)
        assertEquals(7, week.bars.size)
        assertEquals("11/22", week.bars[0].label)
        assertEquals(1000L, week.bars[0].steps)
        assertEquals(4602L, week.bars[1].steps)
    }

    @Test
    fun `aggregateMonth and aggregateYear produce expected bar counts and totals`() {
        val records = listOf(
            HourlyCsvRecord(LocalDate.of(2026, 11, 5), 10, 3000L, 1800L, "t"),
            HourlyCsvRecord(LocalDate.of(2026, 11, 20), 10, 5000L, 3000L, "t"),
            HourlyCsvRecord(LocalDate.of(2026, 10, 15), 10, 2000L, 1200L, "t"),
        )

        val month = PeriodAggregator.aggregateMonth(YearMonth.of(2026, 11), records, profile)
        assertEquals("2026年11月", month.headerText)
        assertEquals(8000L, month.metrics.steps)
        assertEquals(30, month.bars.size)
        assertEquals(3000L, month.bars[4].steps)

        val year = PeriodAggregator.aggregateYear(2026, records, profile)
        assertEquals("2026年", year.headerText)
        assertEquals(10000L, year.metrics.steps)
        assertEquals(12, year.bars.size)
        assertEquals(2000L, year.bars[9].steps)
        assertEquals(8000L, year.bars[10].steps)
    }
}
