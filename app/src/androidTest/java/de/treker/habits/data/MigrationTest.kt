package de.treker.habits.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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

    private fun openRaw(): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return openHelper to openHelper.writableDatabase
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
        // Fresh database for this test only.
        context.deleteDatabase(name)
        val (openHelper, db) = openRaw()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `category` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                "`trackingType` TEXT NOT NULL, `target` REAL NOT NULL, `unit` TEXT NOT NULL, `scheduleMode` TEXT NOT NULL, " +
                "`scheduleMask` INTEGER NOT NULL, `onceDate` TEXT, `reminderMinute` INTEGER, `reminderEnabled` INTEGER NOT NULL, " +
                "`createdDate` TEXT NOT NULL, `paused` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `archivedDate` TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, " +
                "`date` TEXT NOT NULL, `titleSnapshot` TEXT NOT NULL, `categorySnapshot` TEXT NOT NULL, `typeSnapshot` TEXT NOT NULL, " +
                "`targetSnapshot` REAL NOT NULL, `unitSnapshot` TEXT NOT NULL, `progress` REAL NOT NULL, `status` TEXT NOT NULL, " +
                "`timerRemainingMillis` INTEGER NOT NULL, `timerDeadlineMillis` INTEGER, `timerRunning` INTEGER NOT NULL, " +
                "`actualDurationMillis` INTEGER NOT NULL, `note` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO habits (title, description, category, icon, color, trackingType, target, unit, scheduleMode, " +
                "scheduleMask, reminderEnabled, createdDate, paused, archived) VALUES " +
                "('Читать', '', 'Обучение', 'book', 4281084582, 'SIMPLE', 1.0, 'раз', 'WEEKLY', 127, 0, '2026-01-05', 0, 0)"
        )
        db.execSQL(
            "INSERT INTO daily_records (habitId, date, titleSnapshot, categorySnapshot, typeSnapshot, targetSnapshot, " +
                "unitSnapshot, progress, status, timerRemainingMillis, timerRunning, actualDurationMillis, note) VALUES " +
                "(1, '2026-01-05', 'Читать', 'Обучение', 'SIMPLE', 1.0, 'раз', 1.0, 'DONE', 0, 0, 0, '')"
        )

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
            assertEquals(0, cursor.getInt(2))
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
        context.deleteDatabase(name)
        val (openHelper, db) = openRaw()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`createdDate` TEXT NOT NULL, `paused` INTEGER NOT NULL, `archived` INTEGER NOT NULL)"
        )
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_1_2.migrate(db)
        assertEquals(1, tables(db).count { it == "user_templates" })
        openHelper.close()
        context.deleteDatabase(name)
    }
}
