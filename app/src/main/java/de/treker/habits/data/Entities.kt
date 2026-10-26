package de.treker.habits.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Здоровье",
    val icon: String = "check",
    val color: Long = 0xFF5C6BC0,
    val trackingType: String = "SIMPLE",
    val target: Double = 1.0,
    val unit: String = "раз",
    val scheduleMode: String = "WEEKLY",
    val scheduleMask: Int = 127,
    val onceDate: String? = null,
    val reminderMinute: Int? = null,
    val reminderEnabled: Boolean = false,
    val createdDate: String,
    val paused: Boolean = false,
    val archived: Boolean = false,
    val archivedDate: String? = null
)

@Entity(
    tableName = "daily_records",
    indices = [Index(value = ["habitId", "date"], unique = true)],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)]
)
data class DailyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    val titleSnapshot: String,
    val categorySnapshot: String,
    val typeSnapshot: String,
    val targetSnapshot: Double,
    val unitSnapshot: String,
    val progress: Double = 0.0,
    val status: String = "PENDING",
    val timerRemainingMillis: Long = 0,
    val timerDeadlineMillis: Long? = null,
    val timerRunning: Boolean = false,
    val actualDurationMillis: Long = 0,
    val note: String = ""
)

@Entity(tableName = "user_templates")
data class UserTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String,
    val icon: String,
    val color: Long,
    val trackingType: String,
    val target: Double,
    val unit: String,
    val scheduleMask: Int
)
