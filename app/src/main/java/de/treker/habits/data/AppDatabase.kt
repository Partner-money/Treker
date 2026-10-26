package de.treker.habits.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id DESC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY id")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun habit(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitBatch(habits: List<HabitEntity>)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("SELECT * FROM daily_records ORDER BY date, id")
    fun observeRecords(): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE date = :date ORDER BY id")
    fun observeRecords(date: String): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE date = :date AND habitId = :habitId LIMIT 1")
    suspend fun record(habitId: Long, date: String): DailyRecordEntity?

    @Query("SELECT * FROM daily_records WHERE id = :id LIMIT 1")
    suspend fun recordById(id: Long): DailyRecordEntity?

    @Query("SELECT * FROM daily_records ORDER BY date, id")
    suspend fun allRecords(): List<DailyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<DailyRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceRecords(records: List<DailyRecordEntity>)

    @Update
    suspend fun updateRecord(record: DailyRecordEntity)

    @Query("DELETE FROM daily_records")
    suspend fun clearRecords()

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("SELECT * FROM user_templates ORDER BY title COLLATE NOCASE")
    fun observeUserTemplates(): Flow<List<UserTemplateEntity>>

    @Query("SELECT * FROM user_templates ORDER BY id")
    suspend fun allUserTemplates(): List<UserTemplateEntity>

    @Insert
    suspend fun insertUserTemplate(template: UserTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserTemplates(templates: List<UserTemplateEntity>)

    @Query("DELETE FROM user_templates")
    suspend fun clearUserTemplates()

    @Query("DELETE FROM user_templates WHERE id = :id")
    suspend fun deleteUserTemplate(id: Long)
}

@Database(entities = [HabitEntity::class, DailyRecordEntity::class, UserTemplateEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `user_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `trackingType` TEXT NOT NULL, `target` REAL NOT NULL, `unit` TEXT NOT NULL, `scheduleMask` INTEGER NOT NULL)")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "treker.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
