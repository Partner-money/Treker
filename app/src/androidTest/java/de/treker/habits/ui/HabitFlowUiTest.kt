package de.treker.habits.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real activity from an empty database: create a habit through the editor dialog,
 * complete it and check that the same record shows up in "Сегодня" and in Room.
 */
@RunWith(AndroidJUnit4::class)
class HabitFlowUiTest : HabitUiTestSupport() {
    @Test fun createsHabitCompletesItAndShowsItInToday() = runBlocking<Unit> {
        val title = "Утренняя зарядка"

        createHabit(title)

        click("Сделано")
        waitForText("Выполнено")

        val record = record()
        assertEquals("Отметка должна закрыть дневную запись", Tracking.DONE, record.status)
        assertEquals(1.0, record.progress, 0.0)

        // The same day is visible in the "Сегодня" section driven by separate screen state.
        clickTab("Сегодня")
        // "ДНЕВНОЙ ПЛАН" only exists on the today screen, so this proves the section really opened.
        waitForText("ДНЕВНОЙ ПЛАН")
        waitForText(title)
        assertTrue("Выполненная привычка видна в разделе «Сегодня»", exists("Выполнено"))
    }

    @Test fun createdHabitAppearsOnTheNextLaunch() = runBlocking<Unit> {
        val title = "Дневник"

        createHabit(title)

        // Closing and reopening the activity must rebuild the list from Room, not keep it in memory.
        composeRule.activityRule.scenario.recreate()
        waitForText(title)
        assertEquals(1, dao.allHabits().size)
    }

    @Test fun editorRejectsEmptyTitle() = runBlocking<Unit> {
        click("Новая привычка")
        waitForText("Название")
        click("Сохранить")

        waitForText("Укажи название привычки.")
        assertTrue("Пустая привычка не должна сохраняться", dao.allHabits().isEmpty())
        assertTrue("Диалог остаётся открытым с ошибкой", exists("Сохранить"))
    }
}
