package com.cs22study.easygopedometer.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Formats dates explicitly with calendar Month/Day and Weekday in Traditional Chinese
 * to satisfy Taiwan Ministry of Sports screenshot audit rules (never displays ambiguous "今天").
 */
object DateDisplayFormatter {

    fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "週一"
        DayOfWeek.TUESDAY -> "週二"
        DayOfWeek.WEDNESDAY -> "週三"
        DayOfWeek.THURSDAY -> "週四"
        DayOfWeek.FRIDAY -> "週五"
        DayOfWeek.SATURDAY -> "週六"
        DayOfWeek.SUNDAY -> "週日"
    }

    /**
     * e.g. "2026年11月25日 週三"
     */
    fun formatDayHeader(date: LocalDate): String {
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date.dayOfWeek)}"
    }

    /**
     * Returns [start, end] of the week containing [date] using [weekStart] (default Sunday, matching reference UI).
     */
    fun weekBounds(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.SUNDAY): Pair<LocalDate, LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
        val end = start.plusDays(6)
        return start to end
    }

    /**
     * e.g. "11月22日 - 11月28日" (adds year if crossing years)
     */
    fun formatWeekHeader(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.SUNDAY): String {
        val (start, end) = weekBounds(date, weekStart)
        return if (start.year == end.year) {
            "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
        } else {
            "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }
    }

    /**
     * e.g. "2026年11月"
     */
    fun formatMonthHeader(yearMonth: YearMonth): String = "${yearMonth.year}年${yearMonth.monthValue}月"

    /**
     * e.g. "2026年"
     */
    fun formatYearHeader(year: Int): String = "${year}年"
}
