package de.treker.habits.data

import androidx.room.withTransaction
import de.treker.habits.domain.Tracking
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val db: AppDatabase) {
    private val dao = db.habitDao()
    val habits: Flow<List<HabitEntity>> = dao.observeHabits()
    val allRecords: Flow<List<DailyRecordEntity>> = dao.observeRecords()
    val userTemplates: Flow<List<UserTemplateEntity>> = dao.observeUserTemplates()

    fun recordsFor(date: LocalDate): Flow<List<DailyRecordEntity>> = dao.observeRecords(date.toString())

    suspend fun ensureDay(date: LocalDate) = db.withTransaction { ensureDayRows(date) }

    private suspend fun ensureDayRows(date: LocalDate) {
        val snapshots = dao.allHabits().filter { habit ->
            !habit.paused && habit.createdDate <= date.toString() &&
                (habit.archivedDate == null || habit.archivedDate >= date.toString()) &&
                Tracking.isScheduled(habit.scheduleMode, habit.scheduleMask, habit.onceDate, date)
        }.map { habit ->
            DailyRecordEntity(
                habitId = habit.id,
                date = date.toString(),
                titleSnapshot = habit.title,
                categorySnapshot = habit.category,
                typeSnapshot = habit.trackingType,
                targetSnapshot = habit.target,
                unitSnapshot = habit.unit
            )
        }
        dao.insertRecords(snapshots)
    }

    suspend fun saveHabit(habit: HabitEntity, date: LocalDate): Long = db.withTransaction {
        val id = if (habit.id == 0L) dao.insertHabit(habit) else {
            dao.updateHabit(habit)
            val current = dao.record(habit.id, date.toString())
            if (current != null && current.status != Tracking.DONE) {
                val typeChanged = current.typeSnapshot != habit.trackingType
                val goalChanged = current.targetSnapshot != habit.target || typeChanged
                dao.updateRecord(current.copy(
                    titleSnapshot = habit.title, categorySnapshot = habit.category, typeSnapshot = habit.trackingType,
                    targetSnapshot = habit.target, unitSnapshot = habit.unit,
                    progress = if (typeChanged) 0.0 else current.progress,
                    status = if (typeChanged) Tracking.PENDING else current.status,
                    timerRemainingMillis = if (goalChanged) 0L else current.timerRemainingMillis,
                    timerDeadlineMillis = if (goalChanged) null else current.timerDeadlineMillis,
                    timerRunning = if (goalChanged) false else current.timerRunning,
                    actualDurationMillis = if (goalChanged) 0L else current.actualDurationMillis
                ))
            }
            habit.id
        }
        ensureDayRows(date)
        id
    }

    suspend fun changeProgress(recordId: Long, amount: Double) {
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        val value = if (record.typeSnapshot == Tracking.SIMPLE) {
            if (record.status == Tracking.DONE) 0.0 else 1.0
        } else Tracking.clampProgress(record.progress + amount)
        val status = when {
            record.typeSnapshot == Tracking.SIMPLE && value > 0 -> Tracking.DONE
            Tracking.completed(record.typeSnapshot, value, record.targetSnapshot) -> Tracking.DONE
            record.status == Tracking.SKIPPED || record.status == Tracking.FAILED -> record.status
            else -> Tracking.PENDING
        }
        dao.updateRecord(record.copy(progress = value, status = status, timerRunning = false, timerDeadlineMillis = null))
    }

    suspend fun setStatus(recordId: Long, status: String) {
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        dao.updateRecord(record.copy(status = status, timerRunning = false, timerDeadlineMillis = null))
    }

    suspend fun startTimer(recordId: Long, now: Long = System.currentTimeMillis()) {
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        val remaining = record.timerRemainingMillis.takeIf { it > 0 } ?: (record.targetSnapshot * 60_000).toLong()
        dao.updateRecord(record.copy(timerRemainingMillis = remaining, timerDeadlineMillis = now + remaining, timerRunning = true, status = Tracking.PENDING))
    }

    suspend fun pauseTimer(recordId: Long, now: Long = System.currentTimeMillis()) {
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        val remaining = ((record.timerDeadlineMillis ?: now) - now).coerceAtLeast(0L)
        val elapsedSegment = (record.timerRemainingMillis - remaining).coerceAtLeast(0L)
        dao.updateRecord(record.copy(timerRemainingMillis = remaining, timerDeadlineMillis = null, timerRunning = false, actualDurationMillis = record.actualDurationMillis + elapsedSegment))
    }

    suspend fun resetTimer(recordId: Long) {
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        dao.updateRecord(record.copy(timerRemainingMillis = (record.targetSnapshot * 60_000).toLong(), timerDeadlineMillis = null, timerRunning = false, actualDurationMillis = 0, progress = 0.0, status = Tracking.PENDING))
    }

    suspend fun completeElapsedTimers(now: Long = System.currentTimeMillis()) = db.withTransaction {
        dao.allRecords().filter { it.timerRunning && (it.timerDeadlineMillis ?: Long.MAX_VALUE) <= now }.forEach { record ->
            val duration = record.actualDurationMillis + record.timerRemainingMillis
            dao.updateRecord(record.copy(progress = record.targetSnapshot, status = Tracking.DONE, timerRemainingMillis = 0, timerDeadlineMillis = null, timerRunning = false, actualDurationMillis = duration))
        }
    }

    suspend fun addAccumulatedSession(recordId: Long, minutes: Int) {
        if (minutes <= 0) return
        val record = dao.allRecords().firstOrNull { it.id == recordId } ?: return
        val added = minutes.toDouble()
        val progress = minOf(record.targetSnapshot, record.progress + added)
        dao.updateRecord(record.copy(progress = progress, status = if (progress >= record.targetSnapshot) Tracking.DONE else Tracking.PENDING, actualDurationMillis = record.actualDurationMillis + minutes * 60_000L))
    }

    suspend fun updateTitleAndArchive(habit: HabitEntity, title: String?, archived: Boolean, today: LocalDate) {
        dao.updateHabit(habit.copy(title = title?.trim()?.ifBlank { habit.title } ?: habit.title, archived = archived, archivedDate = if (archived) today.toString() else null))
    }

    suspend fun saveAsTemplate(habit: HabitEntity): Long = dao.insertUserTemplate(
        UserTemplateEntity(
            title = habit.title, description = habit.description, category = habit.category, icon = habit.icon,
            color = habit.color, trackingType = habit.trackingType, target = habit.target, unit = habit.unit,
            scheduleMask = if (habit.scheduleMode == "WEEKLY") habit.scheduleMask else 127
        )
    )

    suspend fun deleteTemplate(id: Long) = dao.deleteUserTemplate(id)
    suspend fun recordById(id: Long) = dao.recordById(id)
    suspend fun recordFor(habitId: Long, date: LocalDate) = dao.record(habitId, date.toString())
    suspend fun allHabitsOnce() = dao.allHabits()
    suspend fun allRecordsOnce() = dao.allRecords()
    suspend fun allUserTemplatesOnce() = dao.allUserTemplates()
    suspend fun importData(habits: List<HabitEntity>, records: List<DailyRecordEntity>, templates: List<UserTemplateEntity>, replace: Boolean) = db.withTransaction {
        if (replace) {
            dao.clearRecords()
            dao.clearHabits()
            dao.clearUserTemplates()
        }
        if (habits.isNotEmpty()) dao.insertHabitBatch(habits)
        // Imported record IDs refer to the imported habit IDs; backup import is a full replacement.
        if (records.isNotEmpty()) dao.replaceRecords(records)
        if (templates.isNotEmpty()) dao.insertUserTemplates(templates)
    }
    suspend fun clearHistory() = dao.clearRecords()
    suspend fun resetAll() = db.withTransaction { dao.clearRecords(); dao.clearHabits(); dao.clearUserTemplates() }
}
