package com.hong.volace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, ScheduleRule::class, ScheduleSkip::class, BluetoothRule::class],
    version = 7,
    exportSchema = true,
)
abstract class VolaceDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun bluetoothRuleDao(): BluetoothRuleDao

    companion object {
        /** v1 -> v2: per-profile accent colour and icon. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT " +
                        ProfilePalette.DEFAULT,
                )
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN iconKey TEXT NOT NULL DEFAULT " +
                        "'${ProfileIcon.DEFAULT.key}'",
                )
            }
        }

        /** v2 -> v3: per-stream "変更しない" (keepMask). */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN keepMask INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 -> v4: the schedule (rules and days off). */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minuteOfDay` INTEGER NOT NULL, `days` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_skips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromDay` INTEGER NOT NULL, `toDay` INTEGER NOT NULL)",
                )
            }
        }

        /** v4 -> v5: per-profile default sounds; null leaves them alone, as before. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN ringtoneUri TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN notificationSoundUri TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN alarmSoundUri TEXT")
            }
        }

        /** v5 -> v6: per-profile "Do Not Disturb"; 0 (Volace's modes off) is what profiles did before. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN dndMode INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 -> v7: switching on Bluetooth devices. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bluetooth_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`address` TEXT NOT NULL, `name` TEXT NOT NULL, `profileId` INTEGER NOT NULL, " +
                        "`onDisconnect` INTEGER NOT NULL, `disconnectProfileId` INTEGER, `enabled` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bluetooth_rules_address` ON `bluetooth_rules` (`address`)")
            }
        }

        /**
         * Every migration, oldest first. When bumping the version: add the migration here, commit
         * the new schema JSON under app/schemas, and add a case to MigrationTest.
         */
        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        internal const val NAME = "volace.db"

        /**
         * Deliberately no fallbackToDestructiveMigration: with it, a forgotten migration (or an
         * older APK installed over a newer database) silently dropped every profile, and
         * MainActivity then "repaired" the empty table with the defaults. Failing to open is loud
         * and leaves the data in place to be fixed.
         */
        internal fun builder(context: Context, name: String = NAME) =
            Room.databaseBuilder(context.applicationContext, VolaceDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)

        @Volatile private var instance: VolaceDatabase? = null

        fun get(context: Context): VolaceDatabase =
            instance ?: synchronized(this) {
                instance ?: builder(context).build().also { instance = it }
            }
    }
}
