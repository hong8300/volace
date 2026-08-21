package com.hong.volace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Profile::class], version = 2, exportSchema = false)
abstract class VolaceDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        /** v1 -> v2: per-profile accent colour and icon. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        @Volatile private var instance: VolaceDatabase? = null

        fun get(context: Context): VolaceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VolaceDatabase::class.java,
                    "volace.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    // Personal side-loaded app: never crash on an unexpected schema jump.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
