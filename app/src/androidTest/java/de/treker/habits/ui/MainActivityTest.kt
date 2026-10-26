package de.treker.habits.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesAndShowsPrimaryNavigation() {
        composeRule.onNodeWithText("Привычки").assertExists()
        composeRule.onNodeWithText("Шаблоны").assertExists()
        composeRule.onNodeWithText("Новая привычка").assertExists()
    }
}
