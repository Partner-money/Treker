package de.treker.habits.ui

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.MainActivity
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.DayBoundary
import de.treker.habits.domain.TimeFormat
import de.treker.habits.domain.WeekOrder
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
 * Checks that choices made on the settings screen survive into DataStore and that the destructive
 * actions stay behind a confirmation dialog.
 *
 * The screen is a LazyColumn, so lower rows are reached through performScrollToNode on the list
 * itself: text that was never composed cannot be scrolled to directly.
 */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settingsStore get() = SettingsStore(context)

    private fun currentSettings() = runBlocking { settingsStore.settings.first() }

    @Before fun resetSettings() = runBlocking<Unit> {
        settingsStore.setTheme("SYSTEM")
        settingsStore.setTimeFormat(TimeFormat.H24)
        settingsStore.setFirstDayOfWeek(WeekOrder.MONDAY)
        settingsStore.setDayStartHour(0)
    }

    @After fun restoreSettings() = runBlocking<Unit> {
        settingsStore.setTheme("SYSTEM")
        settingsStore.setTimeFormat(TimeFormat.H24)
        settingsStore.setFirstDayOfWeek(WeekOrder.MONDAY)
        settingsStore.setDayStartHour(0)
    }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) { exists(text) }
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

    /** Scrolls the settings list until [text] is composed and visible, then clicks it. */
    private fun scrollAndClick(text: String) {
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("ПЕРСОНАЛИЗАЦИЯ")))
            .performScrollToNode(hasText(text))
        waitForText(text)
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].performClick()
    }

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
}
