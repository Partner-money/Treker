package de.treker.habits.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupCodecTest {
    private val today = LocalDate.now().toString()

    private fun habit() = HabitEntity(
        id = 4, title = "Вода", category = "Моя категория", trackingType = Tracking.COUNTER,
        target = 6.0, unit = "стаканов", createdDate = today,
        reminderMinute = 9 * 60, reminderEnabled = true,
        soundChoice = "DOUBLE", soundUri = "content://media/external/audio/media/42"
    )

    private fun records() = listOf(
        DailyRecordEntity(
            id = 9, habitId = 4, date = today, titleSnapshot = "Вода", categorySnapshot = "Моя категория",
            typeSnapshot = Tracking.COUNTER, targetSnapshot = 6.0, unitSnapshot = "стаканов", progress = 3.0
        )
    )

    private fun templates() = listOf(
        UserTemplateEntity(
            id = 2, title = "Вода", description = "Пей регулярно", category = "Здоровье", icon = "health",
            color = 0xFF00897B, trackingType = Tracking.COUNTER, target = 6.0, unit = "стаканов", scheduleMask = 127
        )
    )

    private fun settings() = UserSettings(
        theme = "DARK", remindersEnabled = false, soundEnabled = false, accentColor = 0xFF00897B,
        soundChoice = "CUSTOM", customSoundUri = "content://media/external/audio/media/7",
        vibrationEnabled = false, timerLongAlert = true, firstDayOfWeek = "SUNDAY", timeFormat = "12H"
    )

    @Test fun versionThreeRoundTripsHabitsSoundsAndBehaviorSettings() {
        val parsed = BackupCodec.parse(BackupCodec.export(listOf(habit()), records(), templates(), settings()))

        assertEquals(habit(), parsed.habits.single())
        assertEquals(records(), parsed.records)
        assertEquals(templates(), parsed.templates)
        assertEquals(settings(), parsed.settings)
    }

    @Test fun versionOneBackupRemainsImportable() {
        val habit = HabitEntity(id = 1, title = "Читать", createdDate = today)
        val json = JSONObject(BackupCodec.export(listOf(habit), emptyList(), emptyList(), UserSettings()))
            .put("formatVersion", 1)
            .also { it.remove("templates"); it.remove("settings") }

        val parsed = BackupCodec.parse(json.toString())
        assertEquals(listOf(habit), parsed.habits)
        assertTrue(parsed.records.isEmpty())
        assertTrue(parsed.templates.isEmpty())
        assertNull(parsed.settings)
    }

    @Test fun versionTwoBackupKeepsDefaultsForNewPreferences() {
        val habit = HabitEntity(id = 1, title = "Читать", createdDate = today)
        val json = JSONObject(BackupCodec.export(listOf(habit), emptyList(), emptyList(), UserSettings()))
            .put("formatVersion", 2)
            .put("settings", JSONObject().put("theme", "LIGHT").put("remindersEnabled", true).put("soundEnabled", true))
            .also { root -> root.getJSONArray("habits").getJSONObject(0).remove("soundChoice") }

        val parsed = BackupCodec.parse(json.toString())
        val importedSettings = parsed.settings
        assertEquals("LIGHT", importedSettings?.theme)
        assertEquals("SYSTEM", importedSettings?.soundChoice)
        assertNull(importedSettings?.customSoundUri)
        assertEquals("MONDAY", importedSettings?.firstDayOfWeek)
        assertEquals("24H", importedSettings?.timeFormat)
        assertNull(parsed.habits.single().soundChoice)
    }

    @Test fun unsafeCustomSoundUriIsDropped() {
        val json = JSONObject(BackupCodec.export(listOf(habit()), emptyList(), emptyList(), settings()))
            .put("settings", JSONObject().put("soundChoice", "CUSTOM").put("customSoundUri", "file:///etc/passwd"))
            .also { root ->
                val habitJson = root.getJSONArray("habits").getJSONObject(0)
                habitJson.put("soundUri", "javascript:alert(1)")
            }

        val parsed = BackupCodec.parse(json.toString())
        assertNull(parsed.settings?.customSoundUri)
        assertNull(parsed.habits.single().soundUri)
    }

    @Test fun unsupportedVersionIsRejectedBeforeAnyDatabaseMutation() {
        val result = runCatching { BackupCodec.parse("{\"formatVersion\":99,\"habits\":[],\"records\":[]}") }
        assertTrue(result.isFailure)
    }
}
