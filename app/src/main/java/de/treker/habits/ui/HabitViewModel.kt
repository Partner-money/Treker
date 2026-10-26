package de.treker.habits.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.treker.habits.data.AppDatabase
import de.treker.habits.data.BackupCodec
import de.treker.habits.data.DailyRecordEntity
import de.treker.habits.data.HabitEntity
import de.treker.habits.data.HabitRepository
import de.treker.habits.data.SettingsStore
import de.treker.habits.data.UserSettings
import de.treker.habits.notifications.ReminderScheduler
import de.treker.habits.notifications.SoundPlayer
import de.treker.habits.notifications.TimerAlarm
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HabitRepository(AppDatabase.get(application))
    private val settingsStore = SettingsStore(application)
    val selectedDate = MutableStateFlow(LocalDate.now())
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    val habits = repository.habits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val userTemplates = repository.userTemplates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val records = combine(repository.allRecords, selectedDate) { all, date -> all.filter { it.date == date.toString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.completeElapsedTimers()
            repository.ensureDay(LocalDate.now())
            val enabled = settingsStore.settings.first().remindersEnabled
            ReminderScheduler.syncAll(getApplication(), enabled)
            repository.allRecordsOnce().filter { it.timerRunning && it.timerDeadlineMillis != null }.forEach { TimerAlarm.schedule(getApplication(), it.id, it.timerDeadlineMillis!!) }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        viewModelScope.launch { repository.ensureDay(date) }
    }

    fun saveHabit(habit: HabitEntity) = viewModelScope.launch {
        val id = repository.saveHabit(habit, LocalDate.now())
        repository.allHabitsOnce().firstOrNull { it.id == id }?.let { ReminderScheduler.sync(getApplication(), it, settings.value.remindersEnabled) }
        val timerRecord = repository.recordFor(id, LocalDate.now())
        if (timerRecord?.timerRunning == true && timerRecord.timerDeadlineMillis != null) TimerAlarm.schedule(getApplication(), timerRecord.id, timerRecord.timerDeadlineMillis)
        else if (timerRecord != null) TimerAlarm.cancel(getApplication(), timerRecord.id)
    }
    fun progress(record: DailyRecordEntity, amount: Double) = viewModelScope.launch { repository.changeProgress(record.id, amount) }
    fun status(record: DailyRecordEntity, value: String) = viewModelScope.launch { repository.setStatus(record.id, value); TimerAlarm.cancel(getApplication(), record.id) }
    fun startTimer(record: DailyRecordEntity) = viewModelScope.launch {
        repository.startTimer(record.id)
        repository.recordById(record.id)?.timerDeadlineMillis?.let { TimerAlarm.schedule(getApplication(), record.id, it) }
    }
    fun pauseTimer(record: DailyRecordEntity) = viewModelScope.launch { repository.pauseTimer(record.id); TimerAlarm.cancel(getApplication(), record.id) }
    fun resetTimer(record: DailyRecordEntity) = viewModelScope.launch { repository.resetTimer(record.id); TimerAlarm.cancel(getApplication(), record.id) }
    fun finishElapsedTimers() = viewModelScope.launch { repository.completeElapsedTimers() }
    fun addSession(record: DailyRecordEntity, minutes: Int) = viewModelScope.launch { repository.addAccumulatedSession(record.id, minutes) }
    fun saveAsTemplate(habit: HabitEntity) = viewModelScope.launch { repository.saveAsTemplate(habit) }
    fun deleteTemplate(templateId: Long) = viewModelScope.launch { repository.deleteTemplate(templateId) }
    fun togglePause(habit: HabitEntity) = viewModelScope.launch { repository.saveHabit(habit.copy(paused = !habit.paused), LocalDate.now()); ReminderScheduler.sync(getApplication(), habit.copy(paused = !habit.paused), settings.value.remindersEnabled) }
    fun archive(habit: HabitEntity) = viewModelScope.launch { repository.updateTitleAndArchive(habit, null, true, LocalDate.now()); ReminderScheduler.cancel(getApplication(), habit.id) }
    fun rename(habit: HabitEntity, title: String) = viewModelScope.launch { repository.saveHabit(habit.copy(title = title), selectedDate.value) }
    fun setTheme(value: String) = viewModelScope.launch { settingsStore.setTheme(value) }
    fun setReminders(value: Boolean) = viewModelScope.launch { settingsStore.setReminders(value); ReminderScheduler.syncAll(getApplication(), value) }
    fun setSound(value: Boolean) = viewModelScope.launch { settingsStore.setSound(value) }
    fun setAccentColor(value: Long) = viewModelScope.launch { settingsStore.setAccent(value) }
    fun setSoundChoice(value: String) = viewModelScope.launch { settingsStore.setSoundChoice(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { settingsStore.setVibration(value) }
    fun setTimerLongAlert(value: Boolean) = viewModelScope.launch { settingsStore.setTimerLongAlert(value) }
    fun setFirstDayOfWeek(value: String) = viewModelScope.launch { settingsStore.setFirstDayOfWeek(value) }
    fun setTimeFormat(value: String) = viewModelScope.launch { settingsStore.setTimeFormat(value) }

    /** Stores a user-picked audio file; the URI is kept readable through a persisted permission. */
    fun setCustomSound(uri: String?) = viewModelScope.launch {
        settingsStore.setCustomSound(uri)
        if (uri != null) settingsStore.setSoundChoice("CUSTOM")
    }

    fun previewSound(choice: String, customUri: String?, vibrate: Boolean) =
        SoundPlayer.preview(getApplication(), choice, customUri, vibrate)

    fun stopSoundPreview() = SoundPlayer.stop()

    suspend fun exportJson(): String {
        val habits = repository.allHabitsOnce()
        val records = repository.allRecordsOnce()
        val templates = repository.allUserTemplatesOnce()
        return BackupCodec.export(habits, records, templates, settingsStore.settings.first())
    }

    suspend fun importJson(json: String) {
        val data = BackupCodec.parse(json)
        repository.allHabitsOnce().forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        repository.allRecordsOnce().forEach { TimerAlarm.cancel(getApplication(), it.id) }
        repository.importData(data.habits, data.records, data.templates, replace = true)
        data.settings?.let { imported ->
            // A backup coming from another device may reference an unreadable URI; fall back to the system sound.
            val readableUri = imported.customSoundUri?.takeIf { uri ->
                runCatching { getApplication<Application>().contentResolver.openInputStream(android.net.Uri.parse(uri))?.close(); true }.getOrDefault(false)
            }
            val sanitized = if (readableUri == null && imported.customSoundUri != null) {
                imported.copy(customSoundUri = null, soundChoice = if (imported.soundChoice == "CUSTOM") "SYSTEM" else imported.soundChoice)
            } else imported
            // Older backups lack the newer keys: keep the current local choice instead of resetting it.
            val current = settingsStore.settings.first()
            val keys = data.settingsKeys
            settingsStore.applyImported(
                current.copy(
                    theme = if ("theme" in keys) sanitized.theme else current.theme,
                    remindersEnabled = if ("remindersEnabled" in keys) sanitized.remindersEnabled else current.remindersEnabled,
                    soundEnabled = if ("soundEnabled" in keys) sanitized.soundEnabled else current.soundEnabled,
                    accentColor = if ("accentColor" in keys) sanitized.accentColor else current.accentColor,
                    soundChoice = if ("soundChoice" in keys) sanitized.soundChoice else current.soundChoice,
                    customSoundUri = if ("customSoundUri" in keys) readableUri else current.customSoundUri,
                    vibrationEnabled = if ("vibrationEnabled" in keys) sanitized.vibrationEnabled else current.vibrationEnabled,
                    timerLongAlert = if ("timerLongAlert" in keys) sanitized.timerLongAlert else current.timerLongAlert,
                    firstDayOfWeek = if ("firstDayOfWeek" in keys) sanitized.firstDayOfWeek else current.firstDayOfWeek,
                    timeFormat = if ("timeFormat" in keys) sanitized.timeFormat else current.timeFormat
                )
            )
        }
        val enabled = settingsStore.settings.first().remindersEnabled
        ReminderScheduler.syncAll(getApplication(), enabled)
        data.records.filter { it.timerRunning && it.timerDeadlineMillis != null }.forEach { TimerAlarm.schedule(getApplication(), it.id, it.timerDeadlineMillis!!) }
    }
    fun clearHistory() = viewModelScope.launch {
        repository.allRecordsOnce().forEach { TimerAlarm.cancel(getApplication(), it.id) }
        repository.clearHistory()
        repository.ensureDay(LocalDate.now())
    }
    fun resetAll() = viewModelScope.launch {
        repository.allHabitsOnce().forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        repository.allRecordsOnce().forEach { TimerAlarm.cancel(getApplication(), it.id) }
        repository.resetAll()
    }
}
