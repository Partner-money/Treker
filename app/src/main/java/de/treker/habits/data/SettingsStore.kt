package de.treker.habits.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(val theme: String = "SYSTEM", val remindersEnabled: Boolean = true, val soundEnabled: Boolean = true, val accentColor: Long = 0xFF5556B8L)

class SettingsStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val ACCENT = longPreferencesKey("accent_color")
    }

    val settings = context.settingsDataStore.data.map { prefs ->
        UserSettings(prefs[Keys.THEME] ?: "SYSTEM", prefs[Keys.REMINDERS] ?: true, prefs[Keys.SOUND] ?: true, prefs[Keys.ACCENT] ?: 0xFF5556B8L)
    }

    suspend fun setTheme(value: String) = context.settingsDataStore.edit { it[Keys.THEME] = value }
    suspend fun setReminders(value: Boolean) = context.settingsDataStore.edit { it[Keys.REMINDERS] = value }
    suspend fun setSound(value: Boolean) = context.settingsDataStore.edit { it[Keys.SOUND] = value }
    suspend fun setAccent(value: Long) = context.settingsDataStore.edit { it[Keys.ACCENT] = value }
}
