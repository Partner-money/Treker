package de.treker.habits.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val theme: String = "SYSTEM",
    val remindersEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val accentColor: Long = 0xFF5556B8L,
    val soundChoice: String = "SYSTEM",
    val customSoundUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val timerLongAlert: Boolean = false,
    val firstDayOfWeek: String = "MONDAY",
    val timeFormat: String = "24H"
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val ACCENT = longPreferencesKey("accent_color")
        val SOUND_CHOICE = stringPreferencesKey("sound_choice")
        val CUSTOM_SOUND = stringPreferencesKey("custom_sound_uri")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val TIMER_LONG_ALERT = booleanPreferencesKey("timer_long_alert")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val TIME_FORMAT = stringPreferencesKey("time_format")
    }

    val settings = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            theme = prefs[Keys.THEME] ?: "SYSTEM",
            remindersEnabled = prefs[Keys.REMINDERS] ?: true,
            soundEnabled = prefs[Keys.SOUND] ?: true,
            accentColor = prefs[Keys.ACCENT] ?: 0xFF5556B8L,
            soundChoice = prefs[Keys.SOUND_CHOICE] ?: "SYSTEM",
            customSoundUri = prefs[Keys.CUSTOM_SOUND],
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            timerLongAlert = prefs[Keys.TIMER_LONG_ALERT] ?: false,
            firstDayOfWeek = prefs[Keys.FIRST_DAY] ?: "MONDAY",
            timeFormat = prefs[Keys.TIME_FORMAT] ?: "24H"
        )
    }

    suspend fun setTheme(value: String) = context.settingsDataStore.edit { it[Keys.THEME] = value }
    suspend fun setReminders(value: Boolean) = context.settingsDataStore.edit { it[Keys.REMINDERS] = value }
    suspend fun setSound(value: Boolean) = context.settingsDataStore.edit { it[Keys.SOUND] = value }
    suspend fun setAccent(value: Long) = context.settingsDataStore.edit { it[Keys.ACCENT] = value }
    suspend fun setSoundChoice(value: String) = context.settingsDataStore.edit { it[Keys.SOUND_CHOICE] = value }
    suspend fun setVibration(value: Boolean) = context.settingsDataStore.edit { it[Keys.VIBRATION] = value }
    suspend fun setTimerLongAlert(value: Boolean) = context.settingsDataStore.edit { it[Keys.TIMER_LONG_ALERT] = value }
    suspend fun setFirstDayOfWeek(value: String) = context.settingsDataStore.edit { it[Keys.FIRST_DAY] = value }
    suspend fun setTimeFormat(value: String) = context.settingsDataStore.edit { it[Keys.TIME_FORMAT] = value }

    suspend fun setCustomSound(uri: String?) = context.settingsDataStore.edit { prefs ->
        if (uri == null) prefs.remove(Keys.CUSTOM_SOUND) else prefs[Keys.CUSTOM_SOUND] = uri
    }

    suspend fun applyImported(settings: UserSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME] = settings.theme
            prefs[Keys.REMINDERS] = settings.remindersEnabled
            prefs[Keys.SOUND] = settings.soundEnabled
            prefs[Keys.ACCENT] = settings.accentColor
            prefs[Keys.SOUND_CHOICE] = settings.soundChoice
            prefs[Keys.VIBRATION] = settings.vibrationEnabled
            prefs[Keys.TIMER_LONG_ALERT] = settings.timerLongAlert
            prefs[Keys.FIRST_DAY] = settings.firstDayOfWeek
            prefs[Keys.TIME_FORMAT] = settings.timeFormat
            if (settings.customSoundUri == null) prefs.remove(Keys.CUSTOM_SOUND) else prefs[Keys.CUSTOM_SOUND] = settings.customSoundUri
        }
    }
}
