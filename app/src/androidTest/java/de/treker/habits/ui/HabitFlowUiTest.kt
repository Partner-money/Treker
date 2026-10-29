package de.treker.habits.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.MainActivity
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real activity from an empty database: create a habit through the editor dialog,
 * complete it and check that the same record shows up in "Сегодня" and in Room.
 *
 * Text nodes are clicked through the unmerged semantics tree, because Material buttons and
 * navigation items do not merge their label into the parent node.
 */
@RunWith(AndroidJUnit4::class)
class HabitFlowUiTest {
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

    private fun click(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].performClick()
    }

    /** Taps a bottom navigation item: the label alone is also drawn as plain text on the screens. */
    private fun clickTab(label: String) {
        val matcher = hasText(label) and hasClickAction()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(matcher)[0].performClick()
    }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun createsHabitCompletesItAndShowsItInToday() = runBlocking {
        val title = "Утренняя зарядка"

        click("Новая привычка")
        composeRule.waitUntil(10_000) { exists("Название") }

        // The first text field of the editor is the habit title.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput(title)
        click("Сохранить")

        composeRule.waitUntil(10_000) { exists(title) }
        assertEquals("Привычка должна сохраниться в Room", title, dao.allHabits().single().title)

        click("Сделано")
        composeRule.waitUntil(10_000) { exists("Выполнено") }

        val record = dao.allRecords().single()
        assertEquals("Отметка должна закрыть дневную запись", Tracking.DONE, record.status)
        assertEquals(1.0, record.progress, 0.0)

        // The same day is visible in the "Сегодня" section driven by separate screen state.
        clickTab("Сегодня")
        // "ДНЕВНОЙ ПЛАН" only exists on the today screen, so this proves the section really opened.
        composeRule.waitUntil(10_000) { exists("ДНЕВНОЙ ПЛАН") }
        composeRule.waitUntil(10_000) { exists(title) }
        assertTrue("Выполненная привычка видна в разделе «Сегодня»", exists("Выполнено"))
    }

    @Test fun createdHabitAppearsOnTheNextLaunch() = runBlocking {
        val title = "Дневник"

        click("Новая привычка")
        composeRule.waitUntil(10_000) { exists("Название") }
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput(title)
        click("Сохранить")
        composeRule.waitUntil(10_000) { exists(title) }

        // Closing and reopening the activity must rebuild the list from Room, not keep it in memory.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(10_000) { exists(title) }
        assertEquals(1, dao.allHabits().size)
    }

    @Test fun editorRejectsEmptyTitle() = runBlocking {
        click("Новая привычка")
        composeRule.waitUntil(10_000) { exists("Название") }
        click("Сохранить")

        composeRule.waitUntil(10_000) { exists("Укажи название привычки.") }
        assertTrue("Пустая привычка не должна сохраняться", dao.allHabits().isEmpty())
        assertTrue("Диалог остаётся открытым с ошибкой", exists("Сохранить"))
    }
}
