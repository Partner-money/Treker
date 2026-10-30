package de.treker.habits.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A habit day does not have to start at midnight: night owls log "today" after midnight.
 * The setting shifts only the reporting date, never the stored timestamp of a record.
 */
object DayBoundary {
    const val DEFAULT_HOUR = 0
    val allowedHours: List<Int> = (0..6).toList()

    fun normalize(hour: Int?): Int = (hour ?: DEFAULT_HOUR).coerceIn(allowedHours.first(), allowedHours.last())

    /** Reporting date for [now]: before the configured hour it still belongs to the previous date. */
    fun logicalDate(now: LocalDateTime, startHour: Int): LocalDate {
        val hour = normalize(startHour)
        val date = now.toLocalDate()
        return if (hour > 0 && now.hour < hour) date.minusDays(1) else date
    }

    /** Moment when the next habit day begins, used to refresh the open screen. */
    fun nextBoundary(now: LocalDateTime, startHour: Int): LocalDateTime {
        val hour = normalize(startHour)
        val today = DayBoundary.logicalDate(now, hour)
        val boundary = today.plusDays(1).atTime(hour, 0)
        return if (boundary.isAfter(now)) boundary else boundary.plusDays(1)
    }

    fun label(hour: Int): String = String.format(java.util.Locale.US, "%02d:00", normalize(hour))

    fun description(hour: Int): String {
        val normalized = normalize(hour)
        return if (normalized == 0) "Новый день начинается в 00:00." else
            "До ${label(normalized)} приложение считает текущей предыдущую дату."
    }
}
