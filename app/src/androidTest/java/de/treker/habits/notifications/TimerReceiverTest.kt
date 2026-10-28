package de.treker.habits.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.HabitEntity
import de.treker.habits.data.HabitRepository
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.DayBoundary
import de.treker.habits.domain.Tracking
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fires the alarm broadcast TimerAlarm would send and verifies completion plus the notification. */
@RunWith(AndroidJUnit4::class)
class TimerReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database get() = AppDatabase.get(context)
    private val repository get() = HabitRepository(database)
    private val settingsStore by lazy { SettingsStore(context) }
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val today get() = DayBoundary.logicalDate(LocalDateTime.now(), 0)

    @Before fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Before fun clearState() = runBlocking {
        manager.cancelAll()
        database.habitDao().clearRecords()
        database.habitDao().clearHabits()
        settingsStore.setSound(true)
        settingsStore.setTimerLongAlert(false)
    }

    @After fun cleanUp() = runBlocking {
        manager.cancelAll()
        database.habitDao().clearRecords()
        database.habitDao().clearHabits()
    }

    private suspend fun createTimerHabit(targetMinutes: Double = 1.0): Pair<Long, Long> {
        val habit = HabitEntity(
            title = "Медитация",
            category = "Отдых",
            trackingType = Tracking.TIMER,
            target = targetMinutes,
            unit = "мин",
            createdDate = today.minusDays(1).toString()
        )
        val habitId = repository.saveHabit(habit, today)
        val record = repository.recordFor(habitId, today)
        assertNotNull("Дневная запись должна быть создана", record)
        return habitId to record!!.id
    }

    private fun deliverTimer(recordId: Long) {
        context.sendBroadcast(
            Intent(context, TimerReceiver::class.java).setData(Uri.parse("treker://timer/$recordId"))
        )
    }

    private suspend fun await(timeoutMillis: Long = 8_000, condition: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var result = condition()
        while (!result && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
            result = condition()
        }
        return result
    }

    @Test fun elapsedTimerIsCompletedAndNotified() = runBlocking {
        val (_, recordId) = createTimerHabit()
        // Deadline already in the past, as if the alarm fired while the app was closed.
        repository.startTimer(recordId, now = System.currentTimeMillis() - 5 * 60_000L)

        deliverTimer(recordId)

        assertTrue("Таймер должен завершиться", await { repository.recordById(recordId)?.status == Tracking.DONE })
        val record = repository.recordById(recordId)
        assertEquals(60_000L, record?.actualDurationMillis)
        assertTrue("Должно появиться уведомление о завершении", await { manager.activeNotifications.any { it.id == (recordId xor 0x40000000).toInt() } })
    }

    @Test fun runningTimerIsRescheduledInsteadOfCompleted() = runBlocking {
        val (_, recordId) = createTimerHabit(targetMinutes = 30.0)
        repository.startTimer(recordId, now = System.currentTimeMillis())

        deliverTimer(recordId)

        Thread.sleep(1_000)
        val record = repository.recordById(recordId)
        assertEquals("Незавершённый таймер должен остаться в плане", Tracking.PENDING, record?.status)
        assertTrue("Таймер продолжает идти", record?.timerRunning == true)
    }

    @Test fun longAlertSettingDoesNotBreakCompletion() = runBlocking {
        settingsStore.setTimerLongAlert(true)
        val (_, recordId) = createTimerHabit()
        repository.startTimer(recordId, now = System.currentTimeMillis() - 5 * 60_000L)

        deliverTimer(recordId)

        assertTrue(
            "Завершение должно работать и с продолжительным сигналом",
            await { repository.recordById(recordId)?.status == Tracking.DONE }
        )
    }
}
