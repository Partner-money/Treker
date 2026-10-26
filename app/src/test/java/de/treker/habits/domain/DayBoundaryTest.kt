package de.treker.habits.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayBoundaryTest {
    private fun at(date: String, hour: Int, minute: Int = 0) = LocalDateTime.parse("${date}T%02d:%02d:00".format(hour, minute))

    @Test fun midnightBoundaryKeepsCalendarDate() {
        assertEquals(LocalDate.parse("2026-10-27"), DayBoundary.logicalDate(at("2026-10-27", 0, 1), 0))
        assertEquals(LocalDate.parse("2026-10-27"), DayBoundary.logicalDate(at("2026-10-27", 23, 59), 0))
    }

    @Test fun earlyMorningBelongsToPreviousDay() {
        assertEquals(LocalDate.parse("2026-10-26"), DayBoundary.logicalDate(at("2026-10-27", 2, 30), 4))
        assertEquals(LocalDate.parse("2026-10-26"), DayBoundary.logicalDate(at("2026-10-27", 3, 59), 4))
        assertEquals(LocalDate.parse("2026-10-27"), DayBoundary.logicalDate(at("2026-10-27", 4, 0), 4))
        assertEquals(LocalDate.parse("2026-10-27"), DayBoundary.logicalDate(at("2026-10-27", 12, 0), 4))
    }

    @Test fun boundaryCrossesMonthAndYear() {
        assertEquals(LocalDate.parse("2026-12-31"), DayBoundary.logicalDate(at("2027-01-01", 1, 0), 3))
        assertEquals(LocalDate.parse("2026-02-28"), DayBoundary.logicalDate(at("2026-03-01", 2, 0), 3))
    }

    @Test fun hourIsNormalisedToSupportedRange() {
        assertEquals(0, DayBoundary.normalize(null))
        assertEquals(0, DayBoundary.normalize(-5))
        assertEquals(6, DayBoundary.normalize(9))
        assertEquals(6, DayBoundary.normalize(6))
        assertEquals((0..6).toList(), DayBoundary.allowedHours)
        assertEquals("04:00", DayBoundary.label(4))
        assertEquals("00:00", DayBoundary.label(0))
    }

    @Test fun nextBoundaryMovesToTomorrowWhenTodayAlreadyStarted() {
        assertEquals(LocalDateTime.parse("2026-10-27T04:00"), DayBoundary.nextBoundary(at("2026-10-26", 5, 0), 4))
        assertEquals(LocalDateTime.parse("2026-10-27T04:00"), DayBoundary.nextBoundary(at("2026-10-27", 1, 0), 4))
        assertEquals(LocalDateTime.parse("2026-10-28T00:00"), DayBoundary.nextBoundary(at("2026-10-27", 0, 30), 0))
    }

    @Test fun descriptionExplainsSetting() {
        assertEquals("Новый день начинается в 00:00.", DayBoundary.description(0))
        assertEquals("До 04:00 приложение считает текущей предыдущую дату.", DayBoundary.description(4))
    }
}
