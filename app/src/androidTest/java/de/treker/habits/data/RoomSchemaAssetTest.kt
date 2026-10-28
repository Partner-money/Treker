package de.treker.habits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the exported Room schema against the compiled entity code.
 *
 * Room writes `app/schemas/<database>/<version>.json` during the build and (see app/build.gradle.kts)
 * that folder becomes an instrumentation asset. Comparing the exported `identityHash` with the hash
 * Room itself writes into `room_master_table` catches the two mistakes that break real upgrades:
 * editing an entity without bumping the version, and bumping the version without re-exporting the
 * schema. It is the same signal `MigrationTestHelper` would give, without needing committed schemas.
 */
@RunWith(AndroidJUnit4::class)
class RoomSchemaAssetTest {
    private val name = "schema-asset-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val expectedVersion = 3

    private fun schemaAsset(): String? = runCatching {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("de.treker.habits.data.AppDatabase/$expectedVersion.json")
            .bufferedReader().use { it.readText() }
    }.getOrNull()

    @After fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test fun exportedSchemaMatchesCompiledEntities() {
        val asset = schemaAsset()
        assertTrue(
            "Схема Room не экспортирована: app/schemas должны попадать в androidTest assets. " +
                "Проверьте ksp { arg(\"room.schemaLocation\", ...) } и exportSchema = true.",
            asset != null
        )
        val database = JSONObject(asset!!).getJSONObject("database")
        assertEquals("Версия экспортированной схемы", expectedVersion, database.getInt("version"))
        val exportedHash = database.getString("identityHash")
        assertTrue("identityHash не должен быть пустым", exportedHash.isNotBlank())

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val actualHash = room.openHelper.readableDatabase
                .query("SELECT identity_hash FROM room_master_table").use { cursor ->
                    assertTrue("room_master_table должна содержать hash", cursor.moveToFirst())
                    cursor.getString(0)
                }
            assertEquals(
                "Экспортированная схема не совпадает с текущими Room-сущностями: " +
                    "измените версию базы и добавьте миграцию либо пересоберите схему.",
                actualHash,
                exportedHash
            )
        } finally {
            room.close()
        }
    }
}
