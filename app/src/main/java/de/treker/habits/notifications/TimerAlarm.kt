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
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channel = "treker_timer_finished"
                    if (android.os.Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null) {
                        manager.createNotificationChannel(android.app.NotificationChannel(channel, "Завершение таймера", android.app.NotificationManager.IMPORTANCE_DEFAULT))
                    }
                    val notification = NotificationCompat.Builder(context, channel)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Таймер завершён")
                        .setContentText("${record.titleSnapshot} — цель достигнута")
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
