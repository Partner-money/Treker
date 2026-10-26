package de.treker.habits.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.treker.habits.MainActivity
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.HabitEntity
import de.treker.habits.data.SettingsStore
import de.treker.habits.domain.Tracking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReminderScheduler {
    private const val ACTION_REMINDER = "de.treker.habits.ACTION_REMINDER"
    private const val ACTION_BOOT = "android.intent.action.BOOT_COMPLETED"

    fun sync(context: Context, habit: HabitEntity, enabled: Boolean = true) {
        if (!enabled || !habit.reminderEnabled || habit.reminderMinute == null || habit.archived || habit.paused) {
            cancel(context, habit.id)
            return
        }
        val next = nextTrigger(habit, LocalDateTime.now()) ?: run { cancel(context, habit.id); return }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, habit.id)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }

    fun cancel(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, habitId))
    }

    fun syncAll(context: Context, globallyEnabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(context).habitDao()
            dao.allHabits().forEach { sync(context, it, globallyEnabled) }
        }
    }

    private fun nextTrigger(habit: HabitEntity, now: LocalDateTime): Long? {
        val minute = habit.reminderMinute ?: return null
        val time = LocalTime.of(minute / 60, minute % 60)
        val days = if (habit.scheduleMode == "ONCE") listOfNotNull(runCatching { LocalDate.parse(habit.onceDate) }.getOrNull()) else (0L..7L).map { now.toLocalDate().plusDays(it) }
        return days.asSequence().filter { date ->
            date >= now.toLocalDate() && (habit.scheduleMode == "ONCE" || habit.scheduleMask and (1 shl (date.dayOfWeek.value - 1)) != 0)
        }.map { LocalDateTime.of(it, time) }.firstOrNull { it.isAfter(now) }?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    private fun pendingIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER).putExtra("habit_id", habitId).setData(android.net.Uri.parse("treker://reminder/$habitId"))
        return PendingIntent.getBroadcast(context, habitId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val habitId = intent?.getLongExtra("habit_id", -1L) ?: -1L
        if (habitId <= 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = AppDatabase.get(context).habitDao().habit(habitId) ?: return@launch
                val settings = SettingsStore(context).settings.first()
                if (settings.remindersEnabled && habit.reminderEnabled && !habit.paused && !habit.archived) {
                    val record = AppDatabase.get(context).habitDao().record(habit.id, LocalDate.now().toString())
                    if (record?.status != Tracking.DONE) notify(context, habit.title, settings.soundEnabled, habit.id)
                }
                ReminderScheduler.sync(context, habit, settings.remindersEnabled)
            } finally { pendingResult.finish() }
        }
    }

    private fun notify(context: Context, title: String, sound: Boolean, id: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val soundChannel = "treker_reminders_sound"
        val silentChannel = "treker_reminders_silent"
        if (Build.VERSION.SDK_INT >= 26) {
            if (manager.getNotificationChannel(soundChannel) == null) manager.createNotificationChannel(android.app.NotificationChannel(soundChannel, "Напоминания со звуком", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(silentChannel) == null) manager.createNotificationChannel(android.app.NotificationChannel(silentChannel, "Беззвучные напоминания", android.app.NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
        }
        val openApp = PendingIntent.getActivity(context, id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, if (sound) soundChannel else silentChannel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Время для привычки")
            .setContentText(title).setContentIntent(openApp).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_BOOT && intent?.action != "android.intent.action.TIME_SET" && intent?.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsStore(context).settings.first().remindersEnabled
                val db = AppDatabase.get(context)
                db.habitDao().allHabits().forEach { ReminderScheduler.sync(context, it, enabled) }
                db.habitDao().allRecords().filter { it.timerRunning && it.timerDeadlineMillis != null }.forEach { TimerAlarm.schedule(context, it.id, it.timerDeadlineMillis!!) }
            } finally { pendingResult.finish() }
        }
    }
    private companion object { const val ACTION_BOOT = "android.intent.action.BOOT_COMPLETED" }
}
