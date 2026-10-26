package de.treker.habits.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.HabitRepository
import de.treker.habits.domain.Tracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object TimerAlarm {
    fun schedule(context: Context, recordId: Long, deadlineMillis: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMillis, pending(context, recordId))
    }
    fun cancel(context: Context, recordId: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, recordId))
    }
    private fun pending(context: Context, recordId: Long): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).setData(android.net.Uri.parse("treker://timer/$recordId"))
        return PendingIntent.getBroadcast(context, recordId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.data?.lastPathSegment?.toLongOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = HabitRepository(AppDatabase.get(context))
                repository.completeElapsedTimers()
                val record = repository.recordById(id) ?: return@launch
                if (record.typeSnapshot == Tracking.TIMER && record.status == Tracking.DONE) {
                    val settings = de.treker.habits.data.SettingsStore(context).settings.first()
                    val choice = if (settings.soundEnabled) settings.soundChoice else Sounds.SILENT
                    val channel = NotificationChannels.timerChannel(context, choice, settings.customSoundUri, settings.vibrationEnabled, settings.timerLongAlert)
                    val openApp = android.app.PendingIntent.getActivity(
                        context, (id xor 0x40000000).toInt(), android.content.Intent(context, de.treker.habits.MainActivity::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val notification = NotificationCompat.Builder(context, channel)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Таймер завершён")
                        .setContentText("${record.titleSnapshot} — цель достигнута")
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                    runCatching { NotificationManagerCompat.from(context).notify((id xor 0x40000000).toInt(), notification) }
                } else if (record.timerRunning && record.timerDeadlineMillis != null) {
                    TimerAlarm.schedule(context, id, record.timerDeadlineMillis)
                }
            } finally { pending.finish() }
        }
    }
}
