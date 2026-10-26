package de.treker.habits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** First day of week and 12/24-hour clock preferences, kept independent from Android APIs for unit tests. */
object WeekOrder {
    const val MONDAY = "MONDAY"
    const val SUNDAY = "SUNDAY"

    private val MONDAY_FIRST = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    fun normalize(value: String?): String = if (value == SUNDAY) SUNDAY else MONDAY

    fun days(firstDay: String): List<DayOfWeek> =
        if (normalize(firstDay) == SUNDAY) MONDAY_FIRST.drop(1) + MONDAY_FIRST.first() else MONDAY_FIRST

    fun shortLabels(firstDay: String): List<String> = days(firstDay).map { label(it) }

    fun label(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Пн"
        DayOfWeek.TUESDAY -> "Вт"
        DayOfWeek.WEDNESDAY -> "Ср"
        DayOfWeek.THURSDAY -> "Чт"
        DayOfWeek.FRIDAY -> "Пт"
        DayOfWeek.SATURDAY -> "Сб"
        DayOfWeek.SUNDAY -> "Вс"
    }

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /** Start of the week that contains [date] for the configured first day. */
    fun weekStart(date: LocalDate, firstDay: String): LocalDate {
        val order = days(firstDay)
        var current = date
        while (current.dayOfWeek != order.first()) current = current.minusDays(1)
        return current
    }

    /** Aligns a grid so that every column/chunk starts on the configured first day. */
    fun alignedGridStart(date: LocalDate, firstDay: String, days: Int): LocalDate =
        weekStart(date.minusDays((days - 1).toLong()), firstDay)
}

object TimeFormat {
    const val H24 = "24H"
    const val H12 = "12H"

    fun normalize(value: String?): String = if (value == H12) H12 else H24

    /** Formats a minute-of-day value; returns an empty string for "no reminder". */
    fun format(minuteOfDay: Int?, format: String): String {
        if (minuteOfDay == null || minuteOfDay !in 0..1439) return ""
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return if (normalize(format) == H12) {
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val suffix = if (hour < 12) "AM" else "PM"
            String.format(Locale.US, "%d:%02d %s", displayHour, minute, suffix)
        } else {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        }
    }

    /** Accepts "9:30", "09:30", "21:05", "9:30 PM", "12:00 am" and returns minute of day. */
    fun parse(text: String): Int? {
        val cleaned = text.trim().uppercase(Locale.ROOT)
            .replace("\u00A0", " ")
            .replace(Regex("([AP])\\.?\\s*M\\.?"), "$1M")
        val match = Regex("^(\\d{1,2})[: ](\\d{2})\\s*(AM|PM)?$").find(cleaned) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        val meridiem = match.groupValues[3]
        val hour = when {
            meridiem.isEmpty() -> rawHour.takeIf { it in 0..23 }
            rawHour !in 1..12 -> null
            meridiem == "AM" -> if (rawHour == 12) 0 else rawHour
            else -> if (rawHour == 12) 12 else rawHour + 12
        } ?: return null
        return hour * 60 + minute
    }

    /** Editable field value that always uses the 24-hour form so input stays unambiguous. */
    fun editValue(minuteOfDay: Int?): String = format(minuteOfDay, H24)
}
