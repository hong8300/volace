package com.hong.volace.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrations against the schemas exported to app/schemas. 1.json was never exported at the time;
 * it is rebuilt from 2.json minus the columns MIGRATION_1_2 adds.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VolaceDatabase::class.java,
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun migrate1To2_keepsEveryProfileAndAddsDefaultAppearance() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive) VALUES (7, '会議', 3, 1, 0, 0, 12, 5, 9, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true, *VolaceDatabase.MIGRATIONS).use { db ->
            db.query(
                "SELECT name, orderIndex, ringerMode, mediaVolume, alarmVolume, voiceCallVolume, " +
                    "isActive, colorArgb, iconKey FROM profiles WHERE id = 7",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("会議", c.getString(0))
                assertEquals(3, c.getInt(1))
                assertEquals(1, c.getInt(2))
                assertEquals(12, c.getInt(3))
                assertEquals(5, c.getInt(4))
                assertEquals(9, c.getInt(5))
                assertEquals(1, c.getInt(6))
                assertEquals(ProfilePalette.DEFAULT, c.getInt(7))
                assertEquals(ProfileIcon.DEFAULT.key, c.getString(8))
            }
        }
    }

    @Test
    fun migrate2To3_keepsEveryProfileAndChangesNothingByDefault() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive, colorArgb, iconKey) VALUES (3, '音楽', 1, 2, 4, 4, 25, 6, 11, 4, 1, 42, 'music')",
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true, *VolaceDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, mediaVolume, isActive, colorArgb, iconKey, keepMask FROM profiles WHERE id = 3").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("音楽", c.getString(0))
                assertEquals(25, c.getInt(1))
                assertEquals(1, c.getInt(2))
                assertEquals(42, c.getInt(3))
                assertEquals("music", c.getString(4))
                assertEquals(0, c.getInt(5)) // every stream applied, as before
            }
        }
    }

    @Test
    fun migrate3To4_keepsEveryProfileAndAddsAnEmptySchedule() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive, colorArgb, iconKey, keepMask) VALUES (5, 'マナー', 1, 1, 0, 0, 0, 6, 11, 0, 1, 42, 'vibration', 4)",
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true, *VolaceDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, keepMask FROM profiles WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("マナー", c.getString(0))
                assertEquals(4, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM schedule_rules").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.execSQL("INSERT INTO schedule_rules (minuteOfDay, days, profileId, enabled) VALUES (1320, 31, 5, 1)")
            db.execSQL("INSERT INTO schedule_skips (fromDay, toDay) VALUES (20000, 20002)")
        }
    }

    @Test
    fun migrate4To5_keepsEveryProfileAndChangesNoSound() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive, colorArgb, iconKey, keepMask) VALUES (6, '職場', 2, 1, 0, 0, 3, 6, 11, 0, 0, 42, 'work', 0)",
            )
            db.execSQL("INSERT INTO schedule_rules (minuteOfDay, days, profileId, enabled) VALUES (540, 31, 6, 1)")
        }

        helper.runMigrationsAndValidate(DB, 5, true, *VolaceDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, mediaVolume, ringtoneUri, notificationSoundUri, alarmSoundUri FROM profiles WHERE id = 6").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("職場", c.getString(0))
                assertEquals(3, c.getInt(1))
                assertTrue(c.isNull(2) && c.isNull(3) && c.isNull(4)) // "変更しない", as before
            }
            db.query("SELECT COUNT(*) FROM schedule_rules").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate5To6_keepsEveryProfileWithVolaceDndOff() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive, colorArgb, iconKey, keepMask, ringtoneUri) " +
                    "VALUES (8, '夜', 3, 0, 0, 0, 2, 6, 11, 0, 1, 42, 'night', 0, 'content://x/1')",
            )
        }

        helper.runMigrationsAndValidate(DB, 6, true, *VolaceDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, ringtoneUri, dndMode FROM profiles WHERE id = 8").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("夜", c.getString(0))
                assertEquals("content://x/1", c.getString(1))
                assertEquals(0, c.getInt(2))
            }
        }
    }

    /** What the app itself does on launch: every registered migration must get it to the latest. */
    @Test
    fun appBuilder_opensOldestSchemaWithoutLosingData() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive) VALUES ('通常', 0, 2, 5, 5, 15, 6, 11, 5, 0)",
            )
        }

        val db = VolaceDatabase.builder(context, DB).allowMainThreadQueries().build()
        try {
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM profiles").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    /**
     * With no path to the schema it expects (a forgotten migration, or an older APK installed over
     * a newer database) the app must fail to open the database rather than wipe it. It used to
     * drop every table, and MainActivity then refilled the empty table with the defaults.
     */
    @Test
    fun noMigrationPath_failsInsteadOfWiping() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (name, orderIndex, ringerMode, ringVolume, " +
                    "notificationVolume, mediaVolume, alarmVolume, voiceCallVolume, systemVolume, " +
                    "isActive, colorArgb, iconKey, keepMask) VALUES ('通常', 0, 2, 5, 5, 15, 6, 11, 5, 0, 0, 'bell', 0)",
            )
            db.execSQL("PRAGMA user_version = 7") // as written by a future build
        }

        val db = VolaceDatabase.builder(context, DB).allowMainThreadQueries().build()
        assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
        db.close()

        // The profiles are still there for a fixed build to open.
        context.openOrCreateDatabase(DB, Context.MODE_PRIVATE, null).use { raw ->
            raw.rawQuery("SELECT COUNT(*) FROM profiles", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
