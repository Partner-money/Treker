package de.treker.habits.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.MainActivity
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the two habit kinds that live on the daily card beyond a plain checkbox: a counter that
 * has to reach its target, and a timer that has to start, count down and pause.
 *
 * Habits are created through the editor dialog so the app itself generates the daily records.
 * Editor fields are addressed by index because the labels are not part of the text field semantics.
 */
@RunWith(AndroidJUnit4::class)
class HabitTypesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dao get() = AppDatabase.get(context).habitDao()
    private val settingsStore get() = SettingsStore(context)

    @Before fun resetData() = runBlocking<Unit> {
        dao.clearRecords()
        dao.clearHabits()
        dao.clearUserTemplates()
        settingsStore.setReminders(false)
        settingsStore.setDayStartHour(0)
    }

    @After fun cleanUp() = runBlocking<Unit> {
        dao.clearRecords()
        dao.clearHabits()
        settingsStore.setReminders(true)
    }

    private fun record() = runBlocking { dao.allRecords().single() }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) { exists(text) }
    }

    private fun click(text: String) {
        waitForText(text)
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].performClick()
    }

    private fun field(index: Int): androidx.compose.ui.test.SemanticsNodeInteraction =
        composeRule.onAllNodes(hasSetTextAction())[index]

    private fun fillField(index: Int, value: String) {
        field(index).performScrollTo()
        field(index).performTextInput(value)
    }

    private fun createHabit(title: String, typeLabel: String, target: String, unit: String? = null) {
        click("Новая привычка")
        waitForText("Название")
        fillField(0, title)
        click("Тип: Простое действие")
        click(typeLabel)
        field(3).performScrollTo()
        fillField(3, target)
        if (unit != null) fillField(4, unit)
        click("Сохранить")
        waitForText(title)
    }

    @Test fun counterHabitCompletesAtTarget() = runBlocking<Unit> {
        createHabit("Вода", "Счётчик количества", target = "3", unit = "стаканов")
        val habit = dao.allHabits().single()
        assertEquals("Тип должен сохраниться", Tracking.COUNTER, habit.trackingType)
        assertEquals(3.0, habit.target, 0.0)

        click("Сегодня")
        waitForText("+1 стаканов")

        for (step in 1..2) {
            click("+1 стаканов")
            composeRule.waitUntil(10_000) { record().progress == step.toDouble() }
        }
        // The third step reaches the target and must close the day on its own.
        click("+1 стаканов")
        composeRule.waitUntil(10_000) { record().status == Tracking.DONE }

        assertEquals("Прогресс равен цели", 3.0, record().progress, 0.0)
        waitForText("Выполнено")
    }

    @Test fun timerHabitStartsCountsDownAndPauses() = runBlocking<Unit> {
        createHabit("Медитация", "Таймер", target = "1")

        click("Сегодня")
        waitForText("Старт")
        click("Старт")

        composeRule.waitUntil(10_000) { record().timerRunning }
        val running = record()
        assertTrue(
            "У запущенного таймера должен быть дедлайн, примерно через целевую минуту",
            (running.timerDeadlineMillis ?: 0L) > System.currentTimeMillis()
        )
        waitForText("Пауза")

        click("Пауза")
        composeRule.waitUntil(10_000) { !record().timerRunning }

        val paused = record()
        assertTrue("Остаток должен уменьшаться, но не обнуляться", paused.timerRemainingMillis in 1..60_000)
        assertEquals("Дневная запись не должна закрываться по паузе", Tracking.PENDING, paused.status)
        waitForText("Старт")
    }
}
