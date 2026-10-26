package de.treker.habits.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun builtInSoundsArePlayableResources() {
        listOf(Sounds.BEEP, Sounds.DOUBLE, Sounds.ALARM).forEach { choice ->
            val uri = Sounds.uriFor(context, choice, null)
            assertNotNull("Звук $choice должен иметь URI", uri)
            val readable = runCatching { context.contentResolver.openInputStream(uri!!)?.use { it.readBytes().size } }.getOrNull()
            assertTrue("Ресурс $choice должен читаться", (readable ?: 0) > 0)
        }
        assertNull(Sounds.uriFor(context, Sounds.SILENT, null))
    }

    @Test fun customSoundFallsBackToSystemWhenUriIsUnsafe() {
        val own = "content://media/external/audio/media/42"
        assertTrue(Sounds.isAcceptableCustomUri(own))
        assertTrue(!Sounds.isAcceptableCustomUri("file:///etc/passwd"))
        assertNotNull(Sounds.uriFor(context, Sounds.CUSTOM, own))
        assertNotNull("Небезопасный URI не должен ломать звук", Sounds.uriFor(context, Sounds.CUSTOM, "file:///etc/passwd"))
    }

    @Test fun perHabitSoundOverridesGlobalOnlyWhenSet() {
        val inherited = Sounds.effective(null, null, Sounds.DOUBLE, null)
        assertTrue(inherited.first == Sounds.DOUBLE)

        val overridden = Sounds.effective(Sounds.BEEP, null, Sounds.DOUBLE, null)
        assertTrue(overridden.first == Sounds.BEEP)

        val custom = Sounds.effective(Sounds.CUSTOM, "content://media/external/audio/media/5", Sounds.SYSTEM, null)
        assertTrue(custom.first == Sounds.CUSTOM)
        assertTrue(custom.second == "content://media/external/audio/media/5")
    }

    @Test fun reminderAndTimerChannelsAreCreatedWithRequestedSound() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reminderId = NotificationChannels.reminderChannel(context, Sounds.DOUBLE, null, vibration = true)
        val reminder = manager.getNotificationChannel(reminderId)
        assertNotNull(reminder)
        assertTrue(reminder!!.shouldVibrate())
        assertNull("Беззвучный канал не должен иметь звук", NotificationChannels.reminderChannel(context, Sounds.SILENT, null, false).let { manager.getNotificationChannel(it)?.sound })

        val timerId = NotificationChannels.timerChannel(context, Sounds.ALARM, null, vibration = true, longAlert = true)
        val timer = manager.getNotificationChannel(timerId)
        assertNotNull(timer)
        assertTrue(timer!!.importance >= NotificationManager.IMPORTANCE_HIGH)
        assertNotNull(timer.sound)

        // Deterministic ids: the same configuration must reuse the same channel.
        assertTrue(reminderId == NotificationChannels.reminderChannel(context, Sounds.DOUBLE, null, vibration = true))
    }
}
