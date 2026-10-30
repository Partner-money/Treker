package de.treker.habits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

object Tracking {
    const val SIMPLE = "SIMPLE"
    const val COUNTER = "COUNTER"
    const val TIMER = "TIMER"
    const val ACCUMULATE = "ACCUMULATE"

    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
    const val FAILED = "FAILED"

    fun isScheduled(mode: String, mask: Int, onceDate: String?, date: LocalDate): Boolean =
        if (mode == "ONCE") onceDate == date.toString()
        else mask and (1 shl (date.dayOfWeek.value - 1)) != 0

    fun completed(type: String, value: Double, target: Double): Boolean = when (type) {
        SIMPLE -> value > 0.0
        COUNTER, TIMER, ACCUMULATE -> target > 0.0 && value >= target
        else -> false
    }

    fun clampProgress(value: Double): Double = value.coerceAtLeast(0.0)

    /**
     * Activity intensity uses the same whole-percent value the UI shows:
     * 0, 1–33%, 34–66%, 67–99%, 100%. Rounding prevents 2 of 3 (66.7%) from
     * being displayed as "67%" while still painted in the middle level.
     */
    fun completionPercent(planned: Int, done: Int): Int =
        if (planned <= 0) 0 else (done.toDouble() * 100.0 / planned).roundToInt().coerceIn(0, 100)

    fun heatLevel(planned: Int, done: Int): Int {
        if (planned <= 0 || done <= 0) return 0
        return when (completionPercent(planned, done)) {
            100 -> 4
            in 67..99 -> 3
            in 34..66 -> 2
            else -> 1
        }
    }

    fun currentStreak(dailyCompletion: Map<LocalDate, Pair<Int, Int>>, today: LocalDate): Int {
        var streak = 0
        for ((_, result) in dailyCompletion.entries.filter { !it.key.isAfter(today) }.sortedByDescending { it.key }) {
            if (result.first == 0) continue
            if (result.second < result.first) break
            streak++
        }
        return streak
    }

    fun longestStreak(dailyCompletion: Map<LocalDate, Pair<Int, Int>>): Int {
        var best = 0
        var run = 0
        dailyCompletion.toSortedMap().forEach { (_, result) ->
            if (result.first == 0) return@forEach
            if (result.second >= result.first) {
                run++
                best = maxOf(best, run)
            } else run = 0
        }
        return best
    }

    fun weekdayMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
}
