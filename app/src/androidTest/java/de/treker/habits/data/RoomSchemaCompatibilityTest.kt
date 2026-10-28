package de.treker.habits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the real [AppDatabase] on top of a database that still has the version 1 shape.
 *
 * Room validates the schema it finds against the generated entity code right after migrations run,
 * so a mismatch between the migration SQL and the entities fails this test with
 * "Migration didn't properly handle ...". That replaces the identity-hash check of
 * MigrationTestHelper, which needs committed schema JSON (the project has no JDK in the agent
 * container to generate and commit it).
 */
@RunWith(AndroidJUnit4::class)
class RoomSchemaCompatibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "room-compatibility-test.db"

    @Before fun createLegacyDatabase() {
        LegacySchema.open(context, name, withSampleData = true).close()
    }

    @After fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test fun migratedDatabaseIsUsableThroughGeneratedDao() = runBlocking {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val dao = database.habitDao()
        try {
            // Reading through the DAO forces Room to open the file and validate the migrated schema.
            val legacy = dao.allHabits().single()
            assertEquals("Читать", legacy.title)
            assertEquals("Обучение", legacy.category)
            assertTrue("Напоминание из старой версии должно сохраниться", legacy.reminderEnabled)
            assertNull("Новая колонка пустая для старых строк", legacy.soundChoice)
            assertNull(legacy.soundUri)

            val history = dao.allRecords().single()
            assertEquals("DONE", history.status)
            assertEquals(1.0, history.progress, 0.0)

            // New columns must be writable through the generated entity mapping.
            dao.updateHabit(legacy.copy(soundChoice = "BEEP", soundUri = null, title = "Читать вечером"))
            val updated = dao.habit(legacy.id)
            assertEquals("Читать вечером", updated?.title)
            assertEquals("BEEP", updated?.soundChoice)

            // Tables added by the migrations must accept writes as well.
            val today = LocalDate.now().toString()
            val templateId = dao.insertUserTemplate(
                UserTemplateEntity(
                    title = "Вода", description = "", category = "Здоровье", icon = "health",
                    color = 0xFF00897B, trackingType = "COUNTER", target = 6.0, unit = "стаканов", scheduleMask = 127
                )
            )
            assertTrue(templateId > 0)
            assertEquals("Вода", dao.allUserTemplates().single().title)

            dao.insertRecords(
                listOf(
                    DailyRecordEntity(
                        habitId = legacy.id, date = today, titleSnapshot = "Читать", categorySnapshot = "Обучение",
                        typeSnapshot = "SIMPLE", targetSnapshot = 1.0, unitSnapshot = "раз"
                    )
                )
            )
            assertNotNull(dao.record(legacy.id, today))
            assertEquals(2, dao.allRecords().size)
        } finally {
            database.close()
        }
    }
}
