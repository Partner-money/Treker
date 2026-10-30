package de.treker.habits.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import de.treker.habits.MainActivity
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.TimeFormat
import de.treker.habits.domain.Tracking
import de.treker.habits.domain.WeekOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule

/**
 * Shared plumbing for the activity-level UI tests.
 *
 * Every test starts from an empty database and default behaviour settings, so results do not depend
 * on the order the runner picks. Nodes are found through the unmerged tree because Material buttons
 * and navigation items do not merge their label, and screens must be reached through the bottom bar
 * (a plain text click there only hits the label drawn on the current screen).
 */
abstract class HabitUiTestSupport {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val dao get() = AppDatabase.get(context).habitDao()
    protected val settingsStore get() = SettingsStore(context)

    /** The list of the current screen: vertical scrollables are lists, chip rows scroll horizontally. */
    protected val verticalList: SemanticsMatcher =
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Before fun resetData() = runBlocking<Unit> {
        dao.clearRecords()
        dao.clearHabits()
        dao.clearUserTemplates()
        settingsStore.setReminders(false)
        settingsStore.setDayStartHour(0)
        settingsStore.setTheme("SYSTEM")
        settingsStore.setTimeFormat(TimeFormat.H24)
        settingsStore.setFirstDayOfWeek(WeekOrder.MONDAY)
        settingsStore.setShowPercentInStats(true)
    }

    @After fun cleanUp() = runBlocking<Unit> {
        dao.clearRecords()
        dao.clearHabits()
        settingsStore.setReminders(true)
    }

    protected fun records() = runBlocking { dao.allRecords() }

    protected fun record() = records().single()

    protected fun currentSettings() = runBlocking { settingsStore.settings.first() }

    protected fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Some rows are one composed string ("12 окт 2026 · Выполнено · Выполнено"), so match a fragment. */
    protected fun existsSubstring(text: String): Boolean =
        composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Fails with the rendered semantics tree, so a missing label explains itself in CI. */
    protected fun waitUntilTrue(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis, condition)
        } catch (error: Throwable) {
            val tree = runCatching { composeRule.onRoot().printToString() }.getOrDefault("дерево недоступно")
            throw AssertionError("Не дождались: $what. Дерево экрана:\n" + tree.take(700), error)
        }
    }

    protected fun waitForText(text: String) = waitUntilTrue("текст «$text»") { exists(text) }

    protected fun click(text: String) {
        waitForText(text)
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].performClick()
    }

    /** Taps a bottom navigation item: its label is also drawn as plain text on several screens. */
    protected fun clickTab(label: String) {
        val matcher = hasText(label) and hasClickAction()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(matcher)[0].performClick()
    }

    /**
     * Scrolls the current list until [text] is composed. The list node is recreated on recomposition,
     * so the scroll is retried a few times before giving up.
     */
    protected fun scrollListTo(text: String) {
        var lastError: Throwable? = null
        repeat(6) { attempt ->
            try {
                composeRule.onNode(verticalList).performScrollToNode(hasText(text))
                composeRule.waitUntil(5_000) { exists(text) }
                return
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(300L * (attempt + 1))
            }
        }
        throw AssertionError("Не удалось прокрутить список к «$text»: ${lastError?.message}", lastError)
    }

    protected fun scrollAndClick(text: String) {
        scrollListTo(text)
        click(text)
    }

    protected fun field(index: Int): SemanticsNodeInteraction =
        composeRule.onAllNodes(hasSetTextAction())[index]

    /**
     * Replaces the whole value: the editor prefills the target field with "1.0".
     * Scrolling is best effort, because fields inside small dialogs have no scrollable parent.
     */
    protected fun fillField(index: Int, value: String) {
        runCatching { field(index).performScrollTo() }
        field(index).performTextReplacement(value)
    }

    protected suspend fun createHabit(
        title: String,
        typeLabel: String = "Простое действие",
        target: String? = null,
        unit: String? = null
    ) {
        click("Новая привычка")
        waitForText("Название")
        fillField(0, title)
        if (typeLabel != SIMPLE_LABEL) {
            click("Тип: Простое действие")
            click(typeLabel)
            if (target != null) {
                field(3).performScrollTo()
                fillField(3, target)
            }
            if (unit != null) fillField(4, unit)
        }
        click("Сохранить")
        waitForText(title)

        val habit = dao.allHabits().single()
        assertEquals("Тип должен сохраниться", typeLabel, trackingLabel(habit.trackingType))
        if (target != null) {
            assertEquals("Цель должна сохраниться ровно как введено", target.toDouble(), habit.target, 0.0)
        }
        if (unit != null) assertEquals("Единица измерения должна сохраниться", unit, habit.unit)
    }

    protected fun trackingLabel(type: String) = when (type) {
        Tracking.SIMPLE -> SIMPLE_LABEL
        Tracking.COUNTER -> "Счётчик количества"
        Tracking.TIMER -> "Таймер"
        else -> "Накопление времени"
    }

    private companion object {
        const val SIMPLE_LABEL = "Простое действие"
    }
}
