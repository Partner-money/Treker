package de.treker.habits.ui

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
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
 * Covers the two habit kinds that live on the daily card beyond a plain checkbox: a counter that has
 * to reach its target, and a timer that has to start, count down and pause.
 *
 * Habits are created through the editor dialog so the app itself generates the daily records, and the
 * interactions are driven from the "Привычки" screen, where the counter button reads "+1" without the
 * habit unit. Editor fields are addressed by index because their labels are not part of the field
 * semantics, and the target field is prefilled with "1.0", so values are always replaced, not appended.
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

    /** Fails with the rendered semantics tree, so a missing label explains itself in CI. */
    private fun waitForText(text: String) {
        try {
            composeRule.waitUntil(10_000) { exists(text) }
        } catch (error: Throwable) {
            val tree = runCatching { composeRule.onRoot().printToString() }.getOrDefault("дерево недоступно")
            throw AssertionError("Не найден текст «$text». Дерево экрана:\n" + tree.take(3000), error)
        }
    }

    private fun click(text: String) {
        waitForText(text)
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].performClick()
    }

    /** Taps a bottom navigation item: the label alone is also drawn as plain text on the screens. */
    private fun clickTab(label: String) {
        val matcher = hasText(label) and hasClickAction()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(matcher)[0].performClick()
    }

    private fun field(index: Int): androidx.compose.ui.test.SemanticsNodeInteraction =
        composeRule.onAllNodes(hasSetTextAction())[index]

    private fun fillField(index: Int, value: String) {
        field(index).performScrollTo()
        field(index).performTextReplacement(value)
    }

    private suspend fun createHabit(title: String, typeLabel: String, target: String, unit: String? = null) {
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

        val habit = dao.allHabits().single()
        assertEquals("Тип должен сохраниться", typeLabel, trackingLabel(habit.trackingType))
        assertEquals("Цель должна сохраниться ровно как введено", target.toDouble(), habit.target, 0.0)
        if (unit != null) assertEquals("Единица измерения должна сохраниться", unit, habit.unit)
    }

    private fun trackingLabel(type: String) = when (type) {
        Tracking.SIMPLE -> "Простое действие"
        Tracking.COUNTER -> "Счётчик количества"
        Tracking.TIMER -> "Таймер"
        else -> "Накопление времени"
    }

    @Test fun counterHabitCompletesAtTarget() = runBlocking<Unit> {
        createHabit("Вода", "Счётчик количества", target = "3", unit = "стаканов")

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
        // Wait for the section to compose before looking for its scrollable list.
        waitForText("ДНЕВНОЙ ПЛАН")
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("ДНЕВНОЙ ПЛАН")))
            .performScrollToNode(hasText("3 / 3 стаканов"))
        waitForText("3 / 3 стаканов")
    }

    @Test fun timerHabitStartsCountsDownAndPauses() = runBlocking<Unit> {
        createHabit("Медитация", "Таймер", target = "1")

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
