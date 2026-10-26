package de.treker.habits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.treker.habits.domain.Tracking
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: HabitRepository

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = HabitRepository(db)
    }

    @After fun closeDatabase() = db.close()

    @Test fun dailyGenerationIsIdempotentAndRespectsWeekdays() = runBlocking {
        val monday = LocalDate.of(2026, 10, 26)
        repository.saveHabit(habit("Пн-ср-пт", date = monday, mask = Tracking.weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))), monday)
        repository.ensureDay(monday)
        repository.ensureDay(monday)
        assertEquals(1, repository.allRecordsOnce().size)
        repository.ensureDay(monday.plusDays(1))
        assertEquals(1, repository.allRecordsOnce().size)
        repository.ensureDay(monday.plusDays(2))
        assertEquals(2, repository.allRecordsOnce().size)
    }

    @Test fun counterProgressCompletesAndCanBeCorrected() = runBlocking {
        val today = LocalDate.now()
        val id = repository.saveHabit(habit("Отжимания", date = today, type = Tracking.COUNTER, target = 30.0), today)
        val record = repository.allRecordsOnce().single { it.habitId == id }
        repository.changeProgress(record.id, 10.0)
        repository.changeProgress(record.id, 20.0)
        assertEquals(Tracking.DONE, repository.recordById(record.id)?.status)
        repository.changeProgress(record.id, -1.0)
        val corrected = repository.recordById(record.id)
        assertEquals(29.0, corrected?.progress ?: -1.0, 0.0)
        assertEquals(Tracking.PENDING, corrected?.status)
        repository.changeProgress(record.id, -100.0)
        assertEquals(0.0, repository.recordById(record.id)?.progress ?: -1.0, 0.0)
    }

    @Test fun timerKeepsElapsedTimeAcrossPauseAndResume() = runBlocking {
        val today = LocalDate.now()
        val id = repository.saveHabit(habit("Чтение", date = today, type = Tracking.TIMER, target = 1.0), today)
        val record = repository.allRecordsOnce().single { it.habitId == id }
        repository.startTimer(record.id, now = 100_000L)
        repository.pauseTimer(record.id, now = 120_000L)
        val paused = repository.recordById(record.id)
        assertEquals(40_000L, paused?.timerRemainingMillis)
        assertEquals(20_000L, paused?.actualDurationMillis)
        repository.startTimer(record.id, now = 200_000L)
        assertEquals(240_000L, repository.recordById(record.id)?.timerDeadlineMillis)
        repository.completeElapsedTimers(now = 240_000L)
        val completed = repository.recordById(record.id)
        assertNotNull(completed)
        assertEquals(Tracking.DONE, completed?.status)
        assertEquals(60_000L, completed?.actualDurationMillis)
    }

    private fun habit(
        name: String,
        date: LocalDate,
        mask: Int = 127,
        type: String = Tracking.SIMPLE,
        target: Double = 1.0
    ) = HabitEntity(
        title = name,
        trackingType = type,
        target = target,
        unit = if (type == Tracking.TIMER) "мин" else "раз",
        scheduleMask = mask,
        createdDate = date.toString()
    )
}
