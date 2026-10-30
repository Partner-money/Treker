package de.treker.habits.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.treker.habits.R

/** Sound catalogue shared by the settings screen, the habit editor and notification channels. */
object Sounds {
    const val SILENT = "SILENT"
    const val SYSTEM = "SYSTEM"
    const val BEEP = "BEEP"
    const val DOUBLE = "DOUBLE"
    const val ALARM = "ALARM"
    const val CUSTOM = "CUSTOM"

    data class Option(val id: String, val label: String, val hint: String)

    val options: List<Option> = listOf(
        Option(SILENT, "Без звука", "Только вибрация или тихое уведомление"),
        Option(BEEP, "Короткий сигнал", "Встроенный одиночный сигнал"),
        Option(DOUBLE, "Двойной сигнал", "Два коротких сигнала"),
        Option(SYSTEM, "Системный звук", "Звук уведомлений Android"),
        Option(ALARM, "Длинный сигнал", "Подходит для завершения таймера"),
        Option(CUSTOM, "Свой файл", "Аудиофайл с устройства")
    )

    val allowedIds: Set<String> = options.map { it.id }.toSet()

    fun normalize(value: String?): String = if (value in allowedIds) value.orEmpty() else SYSTEM

    fun label(value: String?): String = options.firstOrNull { it.id == normalize(value) }?.label ?: "Системный звук"

    /** A content URI is the only custom sound we accept, so backups and intents cannot inject arbitrary schemes. */
    fun isAcceptableCustomUri(value: String?): Boolean = value != null && value.startsWith("content://")

    fun effective(perHabitChoice: String?, perHabitUri: String?, globalChoice: String, globalUri: String?): Pair<String, String?> {
        val choice = if (perHabitChoice.isNullOrBlank()) globalChoice else perHabitChoice
        val uri = if (choice == CUSTOM) (perHabitUri ?: globalUri) else null
        return normalize(choice) to uri
    }

    fun uriFor(context: Context, choice: String, customUri: String?): Uri? = when (normalize(choice)) {
        SILENT -> null
        SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        BEEP -> resourceUri(context, R.raw.treker_beep)
        DOUBLE -> resourceUri(context, R.raw.treker_double)
        ALARM -> resourceUri(context, R.raw.treker_alarm)
        CUSTOM -> customUri?.takeIf { isAcceptableCustomUri(it) }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun resourceUri(context: Context, id: Int): Uri = Uri.parse("android.resource://${context.packageName}/$id")

    val notificationAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val vibrationPattern: LongArray = longArrayOf(0, 250, 200, 250)
}

/**
 * Plays a short preview without leaving background audio running.
 * Preview never overrides the system volume or silent mode.
 */
object SoundPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    fun preview(context: Context, choice: String, customUri: String?, vibrate: Boolean = false) {
        stop()
        if (vibrate) vibrate(context)
        val uri = Sounds.uriFor(context, choice, customUri) ?: return
        runCatching {
            if (Sounds.normalize(choice) == Sounds.SYSTEM) {
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = Sounds.notificationAttributes
                    play()
                }
            } else {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(Sounds.notificationAttributes)
                    setDataSource(context, uri)
                    setOnCompletionListener { stop() }
                    prepare()
                    start()
                }
            }
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { mediaPlayer?.takeIf { it.isPlaying }?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(Sounds.vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(Sounds.vibrationPattern, -1)
            }
        }
    }
}
