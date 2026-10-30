package de.treker.habits.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the habit kinds that live on the daily card beyond a plain checkbox: a counter that has to
 * reach its target, and a timer that has to start, count down and pause.
 */
@RunWith(AndroidJUnit4::class)
class HabitTypesUiTest : HabitUiTestSupport() {
    @Test fun counterHabitCompletesAtTarget() = runBlocking<Unit> {
        createHabit("Вода", typeLabel = "Счётчик количества", target = "3", unit = "стаканов")
        assertEquals(3.0, dao.allHabits().single().target, 0.0)

        for (step in 1..2) {
            click("+1")
            composeRule.waitUntil(10_000) { record().progress == step.toDouble() }
        }
        // The third step reaches the target and must close the day on its own.
        click("+1")
        composeRule.waitUntil(10_000) { record().status == Tracking.DONE }

        assertEquals("Прогресс равен цели", 3.0, record().progress, 0.0)
        // The habit card marks the day as completed instead of leaving it "в плане".
        waitForText("Выполнено")

        // "Сегодня" renders the same record from its own list and summarises the counter with the unit.
        clickTab("Сегодня")
        waitForText("ДНЕВНОЙ ПЛАН")
        composeRule.onNode(verticalList).performScrollToNode(hasText("3 / 3 стаканов"))
        waitForText("3 / 3 стаканов")
    }

    @Test fun timerHabitStartsCountsDownAndPauses() = runBlocking<Unit> {
        createHabit("Медитация", typeLabel = "Таймер", target = "1")

        waitForText("Старт")
        click("Старт")

        composeRule.waitUntil(10_000) { record().timerRunning }
        val running = record()
        assertTrue(
            "У запущенного таймера должен быть дедлайн в будущем",
            (running.timerDeadlineMillis ?: 0L) > System.currentTimeMillis()
        )
        waitForText("Пауза")

        click("Пауза")
        composeRule.waitUntil(10_000) { !record().timerRunning }

        val paused = record()
        assertTrue("Остаток должен уменьшаться, но не обнуляться", paused.timerRemainingMillis in 1..60_000)
        assertEquals("Пауза не закрывает день", Tracking.PENDING, paused.status)
        waitForText("Старт")
    }
}
