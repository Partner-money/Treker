package de.treker.habits.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Прогресс" section is computed from the records the app wrote earlier, so this test completes
 * a habit first and then reads the section that aggregates it.
 */
@RunWith(AndroidJUnit4::class)
class ProgressUiTest : HabitUiTestSupport() {
    @Test fun completedHabitShowsUpInStatistics() = runBlocking<Unit> {
        val title = "Чтение"
        createHabit(title)
        click("Сделано")
        composeRule.waitUntil(10_000) { record().status == Tracking.DONE }

        clickTab("Прогресс")
        waitForText("ТВОЯ ДИНАМИКА")
        assertTrue("Карточки серий видны сразу", exists("Серия сейчас") && exists("Рекорд"))

        // The regularity list aggregates the day that was just completed.
        scrollListTo("Регулярность привычек")
        composeRule.waitUntil(10_000) { exists("1 из 1") }
        assertTrue("Привычка показана как выполненная за день", exists("1 из 1"))

        // The habit row opens its journal of the latest days.
        scrollAndClick(title)
        waitForText("История: $title")
        composeRule.waitUntil(10_000) { exists("Выполнено") }
        assertEquals("История не должна создавать новых записей", 1, records().size)
    }
}
