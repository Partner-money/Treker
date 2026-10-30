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
import de.treker.habits.data.DailyRecordEntity
import de.treker.habits.data.HabitEntity
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.DayBoundary
import de.treker.habits.domain.Tracking
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Delivers the same broadcast AlarmManager would send, so the receiver logic (permission checks,
 * day boundary, done-check, sound channel and re-scheduling) is exercised for real.
 */
@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database get() = AppDatabase.get(context)
    private val settingsStore by lazy { SettingsStore(context) }
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Before fun clearState() = runBlocking<Unit> {
        manager.cancelAll()
        database.habitDao().clearRecords()
        database.habitDao().clearHabits()
        settingsStore.setReminders(true)
        settingsStore.setSound(true)
        settingsStore.setDayStartHour(0)
    }

    @After fun cleanUp() = runBlocking<Unit> {
        manager.cancelAll()
        database.habitDao().clearRecords()
        database.habitDao().clearHabits()
        settingsStore.setReminders(true)
        settingsStore.setDayStartHour(0)
    }

    private fun habit(minute: Int, soundChoice: String? = null, enabled: Boolean = true, hour: Int = 9) = HabitEntity(
        title = "Читать",
        category = "Обучение",
        trackingType = Tracking.SIMPLE,
        reminderMinute = minute,
        reminderEnabled = enabled,
        soundChoice = soundChoice,
        createdDate = DayBoundary.logicalDate(LocalDateTime.now(), hour).minusDays(30).toString()
    )

    private fun deliver(habitId: Long) {
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .setAction("de.treker.habits.ACTION_REMINDER")
                .putExtra("habit_id", habitId)
                .setData(Uri.parse("treker://reminder/$habitId"))
        )
    }

    private fun notificationPosted(id: Long): Boolean = manager.activeNotifications.any { it.id == id.toInt() }

    private fun await(timeoutMillis: Long = 8_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test fun reminderIsDeliveredForPendingHabit() = runBlocking {
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60))
        deliver(id)
        assertTrue("Напоминание должно появиться", await { notificationPosted(id) })
    }

    @Test fun reminderIsSkippedWhenTodayIsAlreadyDone() = runBlocking {
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60))
        val today = DayBoundary.logicalDate(LocalDateTime.now(), 0)
        database.habitDao().insertRecords(
            listOf(
                DailyRecordEntity(
                    habitId = id, date = today.toString(), titleSnapshot = "Читать", categorySnapshot = "Обучение",
                    typeSnapshot = Tracking.SIMPLE, targetSnapshot = 1.0, unitSnapshot = "раз",
                    progress = 1.0, status = Tracking.DONE
                )
            )
        )
        deliver(id)
        Thread.sleep(1_500)
        assertFalse("Выполненная привычка не должна напоминать о себе", notificationPosted(id))
    }

    @Test fun disabledRemindersSuppressNotification() = runBlocking {
        settingsStore.setReminders(false)
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60))
        deliver(id)
        Thread.sleep(1_500)
        assertFalse("Глобально выключенные напоминания не должны приходить", notificationPosted(id))
    }

    @Test fun habitSoundIsUsedForNotificationChannel() = runBlocking {
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60, soundChoice = Sounds.BEEP))
        deliver(id)
        assertTrue(await { notificationPosted(id) })
        val notification = manager.activeNotifications.first { it.id == id.toInt() }
        val channel = if (Build.VERSION.SDK_INT >= 26) manager.getNotificationChannel(notification.notification.channelId) else null
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals("Текст напоминания", "Читать", notification.notification.extras.getString("android.text"))
            assertTrue("Канал должен быть создан", channel != null)
            assertTrue("Звук привычки должен попасть в канал", channel?.name?.contains("Короткий сигнал") == true)
        }
    }

    @Test fun dayBoundaryMovesEarlyMorningToPreviousDate() = runBlocking {
        // With a 04:00 start, a record stored for the previous calendar date is the current habit day.
        settingsStore.setDayStartHour(4)
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60, hour = 4))
        val logical = DayBoundary.logicalDate(LocalDateTime.now(), 4)
        database.habitDao().insertRecords(
            listOf(
                DailyRecordEntity(
                    habitId = id, date = logical.toString(), titleSnapshot = "Читать", categorySnapshot = "Обучение",
                    typeSnapshot = Tracking.SIMPLE, targetSnapshot = 1.0, unitSnapshot = "раз",
                    progress = 1.0, status = Tracking.DONE
                )
            )
        )
        deliver(id)
        Thread.sleep(1_500)
        assertFalse("Запись логического дня должна учитываться при проверке выполнения", notificationPosted(id))
    }

    @Test fun remindersAreRescheduledAfterDelivery() = runBlocking {
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60))
        deliver(id)
        assertTrue(await { notificationPosted(id) })
        fun pendingExists() = android.app.PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction("de.treker.habits.ACTION_REMINDER")
                .putExtra("habit_id", id)
                .setData(Uri.parse("treker://reminder/$id")),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        ) != null
        assertTrue("Следующее напоминание должно быть запланировано повторно", await { pendingExists() })
    }

    @Test fun disabledHabitDoesNotNotify() = runBlocking {
        val id = database.habitDao().insertHabit(habit(minute = 9 * 60, enabled = false))
        deliver(id)
        Thread.sleep(1_500)
        assertFalse("Выключенное напоминание привычки не должно срабатывать", notificationPosted(id))
    }
}
