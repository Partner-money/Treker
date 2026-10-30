package de.treker.habits.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Applies the real migration objects to a database that has the pre-migration shape and data.
 *
 * The project does not commit exported Room schemas, so this test builds the old tables with SQL
 * instead of MigrationTestHelper; it validates the migration SQL and data preservation, not the
 * generated Room identity hash. Enabling `exportSchema` and committing `app/schemas` later allows
 * switching to the standard helper without changing the assertions.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    private fun openRaw(withSampleData: Boolean = false): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val helper = LegacySchema.open(context, name, withSampleData)
        return helper to helper.writableDatabase
    }

    private fun insertSampleData(db: SupportSQLiteDatabase) {
        db.execSQL(LegacySchema.INSERT_HABIT)
        db.execSQL(LegacySchema.INSERT_RECORD)
    }

    private fun tables(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }

    @Test fun migrationFromVersionOneKeepsHabitsAndAddsTemplatesAndSoundColumns() {
        val (openHelper, db) = openRaw()
        insertSampleData(db)

        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)

        assertTrue("Таблица шаблонов должна появиться", "user_templates" in tables(db))
        assertEquals(
            setOf("id", "title", "description", "category", "icon", "color", "trackingType", "target", "unit", "scheduleMask"),
            columns(db, "user_templates")
        )
        assertTrue("Колонка звука привычки", "soundChoice" in columns(db, "habits"))
        assertTrue("Колонка файла звука привычки", "soundUri" in columns(db, "habits"))

        db.query("SELECT title, category, reminderEnabled, soundChoice FROM habits WHERE id = 1").use { cursor ->
            assertTrue("Привычка должна сохраниться", cursor.moveToFirst())
            assertEquals("Читать", cursor.getString(0))
            assertEquals("Обучение", cursor.getString(1))
            assertEquals("Настройка напоминания сохраняется", 1, cursor.getInt(2))
            assertTrue("Новая колонка пустая для старых строк", cursor.isNull(3))
        }
        db.query("SELECT status, progress FROM daily_records WHERE habitId = 1").use { cursor ->
            assertTrue("История должна сохраниться", cursor.moveToFirst())
            assertEquals("DONE", cursor.getString(0))
            assertEquals(1.0, cursor.getDouble(1), 0.0)
        }
        openHelper.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationsAreIdempotentWhenAppliedTwice() {
        val (openHelper, db) = openRaw(withSampleData = false)
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_1_2.migrate(db)
        assertEquals(1, tables(db).count { it == "user_templates" })
        openHelper.close()
        context.deleteDatabase(name)
    }
}
