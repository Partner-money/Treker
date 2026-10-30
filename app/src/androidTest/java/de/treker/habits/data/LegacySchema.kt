package de.treker.habits.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Exact room schema of application version 1, written out as SQL.
 *
 * Both migration tests share it so they cannot drift apart: the compatibility test additionally
 * lets Room validate the migrated result against the generated entity code, which is what caught
 * the foreign key and unique index that a hand-written schema easily forgets.
 */
object LegacySchema {
    const val CREATE_HABITS =
        "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `icon` TEXT NOT NULL, " +
            "`color` INTEGER NOT NULL, `trackingType` TEXT NOT NULL, `target` REAL NOT NULL, `unit` TEXT NOT NULL, " +
            "`scheduleMode` TEXT NOT NULL, `scheduleMask` INTEGER NOT NULL, `onceDate` TEXT, `reminderMinute` INTEGER, " +
            "`reminderEnabled` INTEGER NOT NULL, `createdDate` TEXT NOT NULL, `paused` INTEGER NOT NULL, " +
            "`archived` INTEGER NOT NULL, `archivedDate` TEXT)"

    const val CREATE_DAILY_RECORDS =
        "CREATE TABLE IF NOT EXISTS `daily_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, `titleSnapshot` TEXT NOT NULL, " +
            "`categorySnapshot` TEXT NOT NULL, `typeSnapshot` TEXT NOT NULL, `targetSnapshot` REAL NOT NULL, " +
            "`unitSnapshot` TEXT NOT NULL, `progress` REAL NOT NULL, `status` TEXT NOT NULL, " +
            "`timerRemainingMillis` INTEGER NOT NULL, `timerDeadlineMillis` INTEGER, `timerRunning` INTEGER NOT NULL, " +
            "`actualDurationMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
            "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

    const val CREATE_DAILY_RECORDS_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_records_habitId_date` ON `daily_records` (`habitId`, `date`)"

    const val INSERT_HABIT =
        "INSERT INTO habits (title, description, category, icon, color, trackingType, target, unit, scheduleMode, " +
            "scheduleMask, reminderEnabled, createdDate, paused, archived) VALUES " +
            "('Читать', '', 'Обучение', 'book', 4281084582, 'SIMPLE', 1.0, 'раз', 'WEEKLY', 127, 1, '2026-01-05', 0, 0)"

    const val INSERT_RECORD =
        "INSERT INTO daily_records (habitId, date, titleSnapshot, categorySnapshot, typeSnapshot, targetSnapshot, " +
            "unitSnapshot, progress, status, timerRemainingMillis, timerRunning, actualDurationMillis, note) VALUES " +
            "(1, '2026-01-05', 'Читать', 'Обучение', 'SIMPLE', 1.0, 'раз', 1.0, 'DONE', 0, 0, 0, '')"

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_HABITS)
        db.execSQL(CREATE_DAILY_RECORDS)
        db.execSQL(CREATE_DAILY_RECORDS_INDEX)
    }

    /** Opens [name] as a version 1 database, optionally with the legacy sample row and record. */
    fun open(context: Context, name: String, withSampleData: Boolean = true): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        createTables(db)
        if (withSampleData) {
            db.execSQL(INSERT_HABIT)
            db.execSQL(INSERT_RECORD)
        }
        return helper
    }
}
