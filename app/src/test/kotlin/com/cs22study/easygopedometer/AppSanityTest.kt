package com.cs22study.easygopedometer

import com.cs22study.easygopedometer.calc.DateDisplayFormatter
import com.cs22study.easygopedometer.calc.MetricsCalculator
import com.cs22study.easygopedometer.calc.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AppSanityTest {

    @Test
    fun `app integrates core-calc formatter and metrics calculator`() {
        val label = DateDisplayFormatter.formatDayHeader(LocalDate.of(2026, 11, 25))
        assertEquals("2026年11月25日 週三", label)

        val metrics = MetricsCalculator.calculate(
            steps = 8000L,
            activeSeconds = 4800L,
            profile = UserProfile(weightKg = 68.0, customStrideMeters = 0.70),
        )
        assertEquals(5600L, metrics.distanceMeters)
        assertEquals("01:20:00", metrics.formattedDuration)
    }
}
