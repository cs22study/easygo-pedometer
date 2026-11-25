package com.cs22study.easygopedometer.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class HourlyPoint(
    val hour: Int,
    val cumulativeSteps: Long,
)

data class BarItem(
    val label: String,
    val steps: Long,
)

data class DaySummaryView(
    val date: LocalDate,
    val headerText: String,
    val metrics: CalculatedMetrics,
    val hourlyCurve: List<HourlyPoint>,
)

data class PeriodSummaryView(
    val headerText: String,
    val metrics: CalculatedMetrics,
    val bars: List<BarItem>,
)

/**
 * Pure aggregation functions for Day, Week, Month, and Year views.
 * Automatically ignores soft-deleted records (`isDeleted == true`).
 */
object PeriodAggregator {

    fun aggregateDay(
        date: LocalDate,
        records: List<HourlyCsvRecord>,
        profile: UserProfile,
        maxHourToPlot: Int = 23,
    ): DaySummaryView {
        val active = records.filter { !it.isDeleted && it.date == date }
        val stepsByHour = LongArray(24)
        var totalSteps = 0L
        var totalActiveSeconds = 0L

        for (r in active) {
            if (r.hour in 0..23) {
                stepsByHour[r.hour] += r.steps
            }
            totalSteps += r.steps
            totalActiveSeconds += r.activeSeconds
        }

        val clampedMax = maxHourToPlot.coerceIn(0, 23)
        val curve = mutableListOf<HourlyPoint>()
        var running = 0L
        for (h in 0..clampedMax) {
            running += stepsByHour[h]
            curve.add(HourlyPoint(hour = h, cumulativeSteps = running))
        }

        return DaySummaryView(
            date = date,
            headerText = DateDisplayFormatter.formatDayHeader(date),
            metrics = MetricsCalculator.calculate(totalSteps, totalActiveSeconds, profile),
            hourlyCurve = curve,
        )
    }

    fun aggregateWeek(
        anchorDate: LocalDate,
        records: List<HourlyCsvRecord>,
        profile: UserProfile,
        weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    ): PeriodSummaryView {
        val (start, end) = DateDisplayFormatter.weekBounds(anchorDate, weekStart)
        val active = records.filter { !it.isDeleted && !it.date.isBefore(start) && !it.date.isAfter(end) }

        val dailySteps = mutableMapOf<LocalDate, Long>()
        var totalSteps = 0L
        var totalActiveSeconds = 0L
        for (r in active) {
            dailySteps[r.date] = (dailySteps[r.date] ?: 0L) + r.steps
            totalSteps += r.steps
            totalActiveSeconds += r.activeSeconds
        }

        val bars = (0L..6L).map { offset ->
            val d = start.plusDays(offset)
            val label = if (offset == 0L || d.dayOfMonth == 1) {
                "${d.monthValue}/${d.dayOfMonth}"
            } else {
                d.dayOfMonth.toString()
            }
            BarItem(label = label, steps = dailySteps[d] ?: 0L)
        }

        return PeriodSummaryView(
            headerText = DateDisplayFormatter.formatWeekHeader(anchorDate, weekStart),
            metrics = MetricsCalculator.calculate(totalSteps, totalActiveSeconds, profile),
            bars = bars,
        )
    }

    fun aggregateMonth(
        yearMonth: YearMonth,
        records: List<HourlyCsvRecord>,
        profile: UserProfile,
    ): PeriodSummaryView {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        val active = records.filter { !it.isDeleted && !it.date.isBefore(start) && !it.date.isAfter(end) }

        val dailySteps = LongArray(yearMonth.lengthOfMonth() + 1)
        var totalSteps = 0L
        var totalActiveSeconds = 0L
        for (r in active) {
            dailySteps[r.date.dayOfMonth] += r.steps
            totalSteps += r.steps
            totalActiveSeconds += r.activeSeconds
        }

        val bars = (1..yearMonth.lengthOfMonth()).map { day ->
            val label = when {
                day == 5 -> "${yearMonth.monthValue}/5"
                day % 5 == 0 -> day.toString()
                else -> ""
            }
            BarItem(label = label, steps = dailySteps[day])
        }

        return PeriodSummaryView(
            headerText = DateDisplayFormatter.formatMonthHeader(yearMonth),
            metrics = MetricsCalculator.calculate(totalSteps, totalActiveSeconds, profile),
            bars = bars,
        )
    }

    fun aggregateYear(
        year: Int,
        records: List<HourlyCsvRecord>,
        profile: UserProfile,
    ): PeriodSummaryView {
        val active = records.filter { !it.isDeleted && it.date.year == year }
        val monthlySteps = LongArray(13)
        var totalSteps = 0L
        var totalActiveSeconds = 0L
        for (r in active) {
            monthlySteps[r.date.monthValue] += r.steps
            totalSteps += r.steps
            totalActiveSeconds += r.activeSeconds
        }

        val bars = (1..12).map { m ->
            BarItem(label = "${m}月", steps = monthlySteps[m])
        }

        return PeriodSummaryView(
            headerText = DateDisplayFormatter.formatYearHeader(year),
            metrics = MetricsCalculator.calculate(totalSteps, totalActiveSeconds, profile),
            bars = bars,
        )
    }
}
