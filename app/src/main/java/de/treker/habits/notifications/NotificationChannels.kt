package de.treker.habits.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Notification channels fix their sound at creation time, so every distinct sound/vibration
 * combination gets a deterministic channel id. Unused Treker channels are removed to keep the
 * system settings screen tidy; recreating the same id restores exactly the same configuration.
 */
object NotificationChannels {
    private const val REMINDER_PREFIX = "treker_reminder_"
    private const val TIMER_PREFIX = "treker_timer_"

    fun reminderChannel(context: Context, choice: String, customUri: String?, vibration: Boolean): String {
        val normalized = Sounds.normalize(choice)
        val name = "Напоминания · ${Sounds.label(normalized)}"
        return ensure(
            context = context,
            prefix = REMINDER_PREFIX,
            name = name,
            choice = normalized,
            customUri = customUri,
            vibration = vibration,
            longAlert = false,
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    fun timerChannel(context: Context, choice: String, customUri: String?, vibration: Boolean, longAlert: Boolean): String {
        val normalized = Sounds.normalize(choice)
        val name = if (longAlert) "Завершение таймера · продолжительный сигнал" else "Завершение таймера · ${Sounds.label(normalized)}"
        return ensure(
            context = context,
            prefix = TIMER_PREFIX,
            name = name,
            choice = if (longAlert) Sounds.ALARM else normalized,
            customUri = customUri,
            vibration = vibration,
            longAlert = longAlert,
            importance = if (longAlert) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    private fun ensure(
        context: Context,
        prefix: String,
        name: String,
        choice: String,
        customUri: String?,
        vibration: Boolean,
        longAlert: Boolean,
        importance: Int
    ): String {
        val signature = "${choice}|${customUri.orEmpty()}|$vibration|$longAlert".hashCode().toUInt().toString(16)
        val id = prefix + signature
        if (Build.VERSION.SDK_INT < 26) return id
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) != null) return id
        val channel = NotificationChannel(id, name, importance).apply {
            description = if (vibration) "Звук: ${Sounds.label(choice)}; вибрация включена" else "Звук: ${Sounds.label(choice)}; без вибрации"
            setShowBadge(true)
            enableVibration(vibration)
            if (vibration) vibrationPattern = Sounds.vibrationPattern
            val uri = Sounds.uriFor(context, choice, customUri)
            if (uri == null) {
                setSound(null, null)
            } else {
                val attributes = if (longAlert) Sounds.alarmAttributes else Sounds.notificationAttributes
                setSound(uri, attributes)
            }
        }
        manager.createNotificationChannel(channel)
        manager.notificationChannels.forEach { existing ->
            if (existing.id != id && existing.id.startsWith(prefix)) {
                runCatching { manager.deleteNotificationChannel(existing.id) }
            }
        }
        return id
    }

    /** App-level switch in Android settings; channel-level choices are shown by the system itself. */
    fun notificationsBlockedBySystem(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return !manager.areNotificationsEnabled()
    }

    fun systemSettingsIntent(context: Context): android.content.Intent =
        android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
