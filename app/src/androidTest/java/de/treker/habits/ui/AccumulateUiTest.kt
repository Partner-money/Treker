package de.treker.habits.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Накопление времени" collects several sessions during the day. The card offers a fixed 15-minute
 * session and a "Другое" dialog for a custom amount; reaching the target closes the day.
 */
@RunWith(AndroidJUnit4::class)
class AccumulateUiTest : HabitUiTestSupport() {
    @Test fun sessionsAccumulateUpToTheTarget() = runBlocking<Unit> {
        createHabit("Английский", typeLabel = "Накопление времени", target = "30")
        assertEquals(Tracking.ACCUMULATE, dao.allHabits().single().trackingType)
        assertEquals("мин", dao.allHabits().single().unit)

        waitForText("15 мин")
        click("15 мин")
        composeRule.waitUntil(10_000) { record().progress == 15.0 }

        // A custom session from the dialog adds on top of the fixed button.
        click("Другое")
        waitForText("Добавить сессию")
        fillField(0, "10")
        click("Добавить")
        composeRule.waitUntil(10_000) { record().progress == 25.0 }
        assertEquals("День ещё не закрыт", Tracking.PENDING, record().status)

        // The next session reaches the target and must stop at it.
        click("15 мин")
        composeRule.waitUntil(10_000) { record().status == Tracking.DONE }

        val finished = record()
        assertEquals("Прогресс не превышает цель", 30.0, finished.progress, 0.0)
        assertEquals("Потраченное время суммируется", (15 + 10 + 15) * 60_000L, finished.actualDurationMillis)
        waitForText("Выполнено")
    }
}
