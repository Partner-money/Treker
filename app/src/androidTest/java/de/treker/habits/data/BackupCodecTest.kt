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
    @Test fun versionTwoRoundTripsHabitsHistoryTemplatesAndSettings() {
        val today = LocalDate.now().toString()
        val habits = listOf(HabitEntity(id = 4, title = "Вода", category = "Моя категория", trackingType = Tracking.COUNTER, target = 6.0, unit = "стаканов", createdDate = today))
        val records = listOf(DailyRecordEntity(id = 9, habitId = 4, date = today, titleSnapshot = "Вода", categorySnapshot = "Моя категория", typeSnapshot = Tracking.COUNTER, targetSnapshot = 6.0, unitSnapshot = "стаканов", progress = 3.0))
        val templates = listOf(UserTemplateEntity(id = 2, title = "Вода", description = "Пей регулярно", category = "Здоровье", icon = "health", color = 0xFF00897B, trackingType = Tracking.COUNTER, target = 6.0, unit = "стаканов", scheduleMask = 127))
        val settings = UserSettings(theme = "DARK", remindersEnabled = false, soundEnabled = false, accentColor = 0xFF00897B)

        val parsed = BackupCodec.parse(BackupCodec.export(habits, records, templates, settings))

        assertEquals(habits, parsed.habits)
        assertEquals(records, parsed.records)
        assertEquals(templates, parsed.templates)
        assertEquals(settings, parsed.settings)
    }

    @Test fun versionOneBackupRemainsImportable() {
        val today = LocalDate.now().toString()
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

    @Test fun unsupportedVersionIsRejectedBeforeAnyDatabaseMutation() {
        val result = runCatching { BackupCodec.parse("{\"formatVersion\":99,\"habits\":[],\"records\":[]}") }
        assertTrue(result.isFailure)
    }
}
