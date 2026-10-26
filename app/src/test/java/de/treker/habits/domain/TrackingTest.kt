package de.treker.habits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingTest {
    @Test fun weekdayScheduleUsesIsoWeekdayBits() {
        val weekdays = Tracking.weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertTrue(Tracking.isScheduled("WEEKLY", weekdays, null, LocalDate.parse("2026-10-26")))
        assertFalse(Tracking.isScheduled("WEEKLY", weekdays, null, LocalDate.parse("2026-10-27")))
        assertTrue(Tracking.isScheduled("WEEKLY", weekdays, null, LocalDate.parse("2026-10-28")))
    }

    @Test fun oneOffScheduleOnlyMatchesItsDate() {
        assertTrue(Tracking.isScheduled("ONCE", 127, "2026-10-26", LocalDate.parse("2026-10-26")))
        assertFalse(Tracking.isScheduled("ONCE", 127, "2026-10-26", LocalDate.parse("2026-10-27")))
    }

    @Test fun completionAndCounterFloorAreConsistent() {
        assertFalse(Tracking.completed(Tracking.COUNTER, 29.0, 30.0))
        assertTrue(Tracking.completed(Tracking.COUNTER, 30.0, 30.0))
        assertEquals(0.0, Tracking.clampProgress(-8.0), 0.0)
        assertTrue(Tracking.completed(Tracking.SIMPLE, 1.0, 1.0))
    }

    @Test fun heatmapIsBasedOnCompletionRatio() {
        assertEquals(0, Tracking.heatLevel(0, 0))
        assertEquals(1, Tracking.heatLevel(4, 1))
        assertEquals(2, Tracking.heatLevel(5, 2))
        assertEquals(3, Tracking.heatLevel(3, 2))
        assertEquals(4, Tracking.heatLevel(3, 3))
    }

    @Test fun streakCountsSuccessfulPlannedDaysAndStopsAtFailure() {
        val today = LocalDate.parse("2026-10-26")
        val outcomes = mapOf(
            today.minusDays(2) to (2 to 2),
            today.minusDays(1) to (1 to 1),
            today to (3 to 3)
        )
        assertEquals(3, Tracking.currentStreak(outcomes, today))
        assertEquals(3, Tracking.longestStreak(outcomes))
        assertEquals(0, Tracking.currentStreak(outcomes + (today to (3 to 2)), today))
    }
}
