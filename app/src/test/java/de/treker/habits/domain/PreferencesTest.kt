package de.treker.habits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesTest {
    @Test fun weekOrderStartsOnConfiguredDay() {
        assertEquals(DayOfWeek.MONDAY, WeekOrder.days(WeekOrder.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, WeekOrder.days(WeekOrder.SUNDAY).first())
        assertEquals(7, WeekOrder.days(WeekOrder.SUNDAY).size)
        assertEquals(listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"), WeekOrder.shortLabels(WeekOrder.SUNDAY))
        assertEquals(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"), WeekOrder.shortLabels(WeekOrder.MONDAY))
        assertEquals(WeekOrder.MONDAY, WeekOrder.normalize(null))
        assertEquals(WeekOrder.MONDAY, WeekOrder.normalize("НЕИЗВЕСТНО"))
    }

    @Test fun weekdayBitsStayIsoAlignedInBothOrders() {
        assertEquals(1, WeekOrder.bit(DayOfWeek.MONDAY))
        assertEquals(1 shl 6, WeekOrder.bit(DayOfWeek.SUNDAY))
        val mondayFirst = WeekOrder.days(WeekOrder.MONDAY)
        val sundayFirst = WeekOrder.days(WeekOrder.SUNDAY)
        mondayFirst.zip(sundayFirst).forEach { (left, right) ->
            assertEquals("Каждый день недели должен иметь одинаковый бит", WeekOrder.bit(left), WeekOrder.bit(right))
        }
    }

    @Test fun weekStartDependsOnFirstDay() {
        val wednesday = LocalDate.parse("2026-10-28")
        assertEquals(LocalDate.parse("2026-10-26"), WeekOrder.weekStart(wednesday, WeekOrder.MONDAY))
        assertEquals(LocalDate.parse("2026-10-25"), WeekOrder.weekStart(wednesday, WeekOrder.SUNDAY))
    }

    @Test fun heatmapGridStartsOnFirstDayOfWeek() {
        val today = LocalDate.parse("2026-10-28")
        WeekOrder.alignedGridStart(today, WeekOrder.MONDAY, 365).let { start ->
            assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals(start, WeekOrder.weekStart(start, WeekOrder.MONDAY))
            val weeks = java.time.temporal.ChronoUnit.DAYS.between(start, WeekOrder.weekStart(today, WeekOrder.MONDAY))
            assertEquals(0L, weeks % 7)
        }
        assertEquals(DayOfWeek.SUNDAY, WeekOrder.alignedGridStart(today, WeekOrder.SUNDAY, 365).dayOfWeek)
    }

    @Test fun timeFormattingFollowsSetting() {
        assertEquals("09:05", TimeFormat.format(9 * 60 + 5, TimeFormat.H24))
        assertEquals("00:00", TimeFormat.format(0, TimeFormat.H24))
        assertEquals("23:59", TimeFormat.format(23 * 60 + 59, TimeFormat.H24))
        assertEquals("9:05 AM", TimeFormat.format(9 * 60 + 5, TimeFormat.H12))
        assertEquals("12:00 AM", TimeFormat.format(0, TimeFormat.H12))
        assertEquals("12:30 PM", TimeFormat.format(12 * 60 + 30, TimeFormat.H12))
        assertEquals("11:59 PM", TimeFormat.format(23 * 60 + 59, TimeFormat.H12))
        assertEquals("", TimeFormat.format(null, TimeFormat.H24))
        assertEquals("", TimeFormat.format(2000, TimeFormat.H24))
        assertEquals(TimeFormat.H24, TimeFormat.normalize("неизвестно"))
    }

    @Test fun timeParsingAcceptsBothFormatsAndRejectsInvalidInput() {
        assertEquals(9 * 60 + 30, TimeFormat.parse("09:30"))
        assertEquals(9 * 60 + 30, TimeFormat.parse("9:30"))
        assertEquals(21 * 60 + 5, TimeFormat.parse("21:05"))
        assertEquals(21 * 60 + 30, TimeFormat.parse("9:30 PM"))
        assertEquals(21 * 60 + 30, TimeFormat.parse("9:30 p.m."))
        assertEquals(0, TimeFormat.parse("12:00 AM"))
        assertEquals(12 * 60, TimeFormat.parse("12:00 PM"))
        assertNull(TimeFormat.parse("24:00"))
        assertNull(TimeFormat.parse("12:60"))
        assertNull(TimeFormat.parse("13:00 PM"))
        assertNull(TimeFormat.parse(""))
        assertNull(TimeFormat.parse("утро"))
    }

    @Test fun editValueAlwaysUsesTwentyFourHourClock() {
        assertEquals("09:30", TimeFormat.editValue(9 * 60 + 30))
        assertEquals("", TimeFormat.editValue(null))
    }
}
