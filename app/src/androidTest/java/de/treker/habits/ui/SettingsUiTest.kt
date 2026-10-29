package de.treker.habits.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.DayBoundary
import de.treker.habits.domain.TimeFormat
import de.treker.habits.domain.Tracking
import de.treker.habits.domain.WeekOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that choices made on the settings screen survive into DataStore and that the destructive
 * actions stay behind a confirmation dialog.
 */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest : HabitUiTestSupport() {
    @Test fun themeChoiceIsPersisted() = runBlocking<Unit> {
        clickTab("Настройки")
        waitForText("ПЕРСОНАЛИЗАЦИЯ")

        click("Тёмная")
        composeRule.waitUntil(10_000) { currentSettings().theme == "DARK" }
        assertEquals("DARK", currentSettings().theme)

        click("Системная")
        composeRule.waitUntil(10_000) { currentSettings().theme == "SYSTEM" }
    }

    @Test fun timeFormatFirstDayAndDayBoundaryArePersisted() = runBlocking<Unit> {
        clickTab("Настройки")
        waitForText("ПЕРСОНАЛИЗАЦИЯ")

        scrollAndClick("12 часов")
        composeRule.waitUntil(10_000) { currentSettings().timeFormat == TimeFormat.H12 }

        scrollAndClick("Воскресенье")
        composeRule.waitUntil(10_000) { currentSettings().firstDayOfWeek == WeekOrder.SUNDAY }

        val shiftedHour = DayBoundary.allowedHours.first { it != DayBoundary.DEFAULT_HOUR }
        scrollAndClick(DayBoundary.label(shiftedHour))
        composeRule.waitUntil(10_000) { currentSettings().dayStartHour == shiftedHour }

        val settings = currentSettings()
        assertEquals(TimeFormat.H12, settings.timeFormat)
        assertEquals(WeekOrder.SUNDAY, settings.firstDayOfWeek)
        assertEquals(shiftedHour, settings.dayStartHour)
    }

    @Test fun resetRequiresConfirmationAndCanBeCancelled() = runBlocking<Unit> {
        clickTab("Настройки")
        waitForText("ПЕРСОНАЛИЗАЦИЯ")

        scrollAndClick("Сбросить приложение")
        waitForText("Удалить все данные?")

        click("Отмена")
        composeRule.waitUntil(10_000) { !exists("Удалить все данные?") }
        assertTrue("После отмены диалог закрывается", !exists("Удалить всё"))
    }

    @Test fun clearingHistoryDropsResultsButKeepsHabits() = runBlocking<Unit> {
        createHabit("Дневник")
        click("Сделано")
        composeRule.waitUntil(10_000) { record().status == Tracking.DONE }

        clickTab("Настройки")
        waitForText("ПЕРСОНАЛИЗАЦИЯ")
        scrollAndClick("Очистить историю")
        waitForText("Очистить историю?")

        click("Очистить")
        // Clearing history recreates the open day, so the habit keeps a fresh pending record.
        composeRule.waitUntil(10_000) { records().none { it.status == Tracking.DONE } }

        val remaining = record()
        assertEquals("Привычка должна остаться", 1, dao.allHabits().size)
        assertEquals("Дневной результат очищен", Tracking.PENDING, remaining.status)
        assertEquals(0.0, remaining.progress, 0.0)
    }
}
