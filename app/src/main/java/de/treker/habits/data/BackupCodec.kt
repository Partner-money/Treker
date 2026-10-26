package de.treker.habits.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val habits: List<HabitEntity>,
    val records: List<DailyRecordEntity>,
    val templates: List<UserTemplateEntity>,
    val settings: UserSettings?,
    /** Keys actually present in the file, so older backups do not overwrite newer local preferences. */
    val settingsKeys: Set<String> = emptySet()
)

object BackupCodec {
    fun export(habits: List<HabitEntity>, records: List<DailyRecordEntity>, templates: List<UserTemplateEntity>, settings: UserSettings): String {
        val root = JSONObject().put("formatVersion", 3).put("app", "Treker")
            .put("settings", JSONObject()
                .put("theme", settings.theme)
                .put("remindersEnabled", settings.remindersEnabled)
                .put("soundEnabled", settings.soundEnabled)
                .put("accentColor", settings.accentColor)
                .put("soundChoice", settings.soundChoice)
                .put("customSoundUri", settings.customSoundUri ?: JSONObject.NULL)
                .put("vibrationEnabled", settings.vibrationEnabled)
                .put("timerLongAlert", settings.timerLongAlert)
                .put("firstDayOfWeek", settings.firstDayOfWeek)
                .put("timeFormat", settings.timeFormat))
        root.put("habits", JSONArray().apply {
            habits.forEach { h -> put(JSONObject()
                .put("id", h.id).put("title", h.title).put("description", h.description).put("category", h.category)
                .put("icon", h.icon).put("color", h.color).put("trackingType", h.trackingType).put("target", h.target)
                .put("unit", h.unit).put("scheduleMode", h.scheduleMode).put("scheduleMask", h.scheduleMask)
                .put("onceDate", h.onceDate ?: JSONObject.NULL).put("reminderMinute", h.reminderMinute ?: JSONObject.NULL)
                .put("reminderEnabled", h.reminderEnabled).put("createdDate", h.createdDate).put("paused", h.paused)
                .put("soundChoice", h.soundChoice ?: JSONObject.NULL).put("soundUri", h.soundUri ?: JSONObject.NULL)
                .put("archived", h.archived).put("archivedDate", h.archivedDate ?: JSONObject.NULL)) }
        })
        root.put("records", JSONArray().apply {
            records.forEach { r -> put(JSONObject()
                .put("id", r.id).put("habitId", r.habitId).put("date", r.date).put("titleSnapshot", r.titleSnapshot)
                .put("categorySnapshot", r.categorySnapshot).put("typeSnapshot", r.typeSnapshot).put("targetSnapshot", r.targetSnapshot)
                .put("unitSnapshot", r.unitSnapshot).put("progress", r.progress).put("status", r.status)
                .put("timerRemainingMillis", r.timerRemainingMillis).put("timerDeadlineMillis", r.timerDeadlineMillis ?: JSONObject.NULL)
                .put("timerRunning", r.timerRunning).put("actualDurationMillis", r.actualDurationMillis).put("note", r.note)) }
        })
        root.put("templates", JSONArray().apply {
            templates.forEach { t -> put(JSONObject()
                .put("id", t.id).put("title", t.title).put("description", t.description).put("category", t.category)
                .put("icon", t.icon).put("color", t.color).put("trackingType", t.trackingType).put("target", t.target)
                .put("unit", t.unit).put("scheduleMask", t.scheduleMask)) }
        })
        return root.toString(2)
    }

    /** Validates the whole backup before data replacement; version 1 and 2 backups remain importable. */
    fun parse(json: String): BackupData {
        val root = JSONObject(json)
        val version = root.optInt("formatVersion", -1)
        require(version in 1..3) { "Версия резервной копии не поддерживается" }
        val habitArray = root.getJSONArray("habits")
        val recordArray = root.getJSONArray("records")
        val habits = buildList {
            for (i in 0 until habitArray.length()) {
                val o = habitArray.getJSONObject(i)
                add(HabitEntity(
                    id = o.getLong("id"), title = o.getString("title").trim().also { require(it.isNotEmpty()) },
                    description = o.optString("description"), category = o.optString("category", "Здоровье"),
                    icon = o.optString("icon", "check"), color = o.optLong("color", 0xFF5C6BC0),
                    trackingType = o.optString("trackingType", "SIMPLE"), target = o.optDouble("target", 1.0).coerceAtLeast(0.01),
                    unit = o.optString("unit", "раз"), scheduleMode = o.optString("scheduleMode", "WEEKLY"),
                    scheduleMask = o.optInt("scheduleMask", 127), onceDate = o.nullableString("onceDate"),
                    reminderMinute = o.nullableInt("reminderMinute"), reminderEnabled = o.optBoolean("reminderEnabled"),
                    soundChoice = o.nullableString("soundChoice")?.takeIf { it in SOUND_CHOICES },
                    soundUri = o.nullableString("soundUri")?.takeIf { it.startsWith("content://") },
                    createdDate = o.getString("createdDate"), paused = o.optBoolean("paused"), archived = o.optBoolean("archived"),
                    archivedDate = o.nullableString("archivedDate")
                ))
            }
        }
        val validIds = habits.map { it.id }.toSet()
        val records = buildList {
            for (i in 0 until recordArray.length()) {
                val o = recordArray.getJSONObject(i)
                val habitId = o.getLong("habitId")
                require(habitId in validIds) { "Запись ссылается на отсутствующую привычку" }
                add(DailyRecordEntity(
                    id = o.getLong("id"), habitId = habitId, date = o.getString("date"),
                    titleSnapshot = o.getString("titleSnapshot"), categorySnapshot = o.optString("categorySnapshot"),
                    typeSnapshot = o.getString("typeSnapshot"), targetSnapshot = o.getDouble("targetSnapshot").coerceAtLeast(0.01),
                    unitSnapshot = o.optString("unitSnapshot", "раз"), progress = o.optDouble("progress").coerceAtLeast(0.0),
                    status = o.optString("status", "PENDING"), timerRemainingMillis = o.optLong("timerRemainingMillis"),
                    timerDeadlineMillis = o.nullableLong("timerDeadlineMillis"), timerRunning = o.optBoolean("timerRunning"),
                    actualDurationMillis = o.optLong("actualDurationMillis").coerceAtLeast(0), note = o.optString("note")
                ))
            }
        }
        val templateArray = if (root.has("templates")) root.getJSONArray("templates") else JSONArray()
        val templates = buildList {
            for (i in 0 until templateArray.length()) {
                val o = templateArray.getJSONObject(i)
                add(UserTemplateEntity(
                    id = o.getLong("id"), title = o.getString("title").trim().also { require(it.isNotEmpty()) },
                    description = o.optString("description"), category = o.optString("category", "Личное"),
                    icon = o.optString("icon", "check"), color = o.optLong("color", 0xFF5C6BC0),
                    trackingType = o.getString("trackingType"), target = o.getDouble("target").coerceAtLeast(0.01),
                    unit = o.optString("unit", "раз"), scheduleMask = o.optInt("scheduleMask", 127)
                ))
            }
        }
        require(habits.map { it.id }.distinct().size == habits.size) { "В резервной копии есть повторяющиеся идентификаторы" }
        require(records.map { it.habitId to it.date }.distinct().size == records.size) { "В резервной копии есть повторяющиеся дневные записи" }
        require(records.map { it.id }.distinct().size == records.size) { "В резервной копии есть повторяющиеся ID записей" }
        require(templates.map { it.id }.distinct().size == templates.size) { "В резервной копии есть повторяющиеся шаблоны" }
        require(habits.all { it.soundUri == null || it.soundUri.startsWith("content://") }) { "В резервной копии есть некорректный звук привычки" }
        require(habits.all { it.id > 0 && runCatching { java.time.LocalDate.parse(it.createdDate) }.isSuccess && it.trackingType in TRACKING_TYPES && it.scheduleMode in setOf("WEEKLY", "ONCE") && (it.scheduleMode != "WEEKLY" || it.scheduleMask in 1..127) && (it.scheduleMode != "ONCE" || runCatching { java.time.LocalDate.parse(it.onceDate) }.isSuccess) && (it.reminderMinute == null || it.reminderMinute in 0..1439) }) { "В резервной копии есть некорректная привычка" }
        require(records.all { it.id > 0 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess && it.status in setOf("PENDING", "DONE", "SKIPPED", "FAILED") && it.typeSnapshot in TRACKING_TYPES }) { "В резервной копии есть некорректная дневная запись" }
        require(templates.all { it.id > 0 && it.trackingType in TRACKING_TYPES && it.scheduleMask in 1..127 }) { "В резервной копии есть некорректный пользовательский шаблон" }
        val settingsObject = root.optJSONObject("settings")
        val settings = settingsObject?.let { prefs ->
            val theme = prefs.optString("theme", "SYSTEM").takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM"
            val allowedAccents = setOf(0xFF5556B8L, 0xFF00897BL, 0xFFE16C5BL, 0xFF8E63BAL, 0xFFE5A33DL, 0xFF3979B9L)
            val accent = prefs.optLong("accentColor", 0xFF5556B8L).takeIf { it in allowedAccents } ?: 0xFF5556B8
            val soundChoice = prefs.optString("soundChoice", "SYSTEM").takeIf { it in SOUND_CHOICES } ?: "SYSTEM"
            val customSound = prefs.nullableString("customSoundUri")?.takeIf { it.startsWith("content://") }
            val firstDay = prefs.optString("firstDayOfWeek", "MONDAY").takeIf { it in setOf("MONDAY", "SUNDAY") } ?: "MONDAY"
            val timeFormat = prefs.optString("timeFormat", "24H").takeIf { it in setOf("24H", "12H") } ?: "24H"
            UserSettings(
                theme = theme,
                remindersEnabled = prefs.optBoolean("remindersEnabled", true),
                soundEnabled = prefs.optBoolean("soundEnabled", true),
                accentColor = accent,
                soundChoice = soundChoice,
                customSoundUri = customSound,
                vibrationEnabled = prefs.optBoolean("vibrationEnabled", true),
                timerLongAlert = prefs.optBoolean("timerLongAlert", false),
                firstDayOfWeek = firstDay,
                timeFormat = timeFormat
            )
        }
        return BackupData(habits, records, templates, settings, settingsObject?.keys()?.asSequence()?.toSet() ?: emptySet())
    }

    private val TRACKING_TYPES = setOf("SIMPLE", "COUNTER", "TIMER", "ACCUMULATE")
    private val SOUND_CHOICES = setOf("SILENT", "SYSTEM", "BEEP", "DOUBLE", "ALARM", "CUSTOM")
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
}
