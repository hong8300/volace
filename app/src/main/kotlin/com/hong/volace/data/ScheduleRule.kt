package com.hong.volace.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * "At [minuteOfDay] on [days], switch to [profileId]" (DESIGN.md 5.15). The schedule only acts at
 * these moments: whatever is chosen by hand in between stays until the next one.
 *
 * No foreign key to the profile: deleting a profile deletes its rules on purpose (the edit screen),
 * but restoring a backup in place of everything gives every profile a new id, and a cascade would
 * then wipe the whole schedule without a word. A rule whose profile is gone is shown as such instead.
 */
@Entity(tableName = "schedule_rules")
data class ScheduleRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0..1439, local time. */
    val minuteOfDay: Int,
    /** One bit per day, Monday first (ScheduleCalc.dayBit). */
    val days: Int,
    val profileId: Long,
    val enabled: Boolean = true,
)

/** Days the schedule rests ("休む日": holidays, time off), [fromDay]..[toDay] as epoch days, inclusive. */
@Entity(tableName = "schedule_skips")
data class ScheduleSkip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromDay: Long,
    val toDay: Long,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_rules ORDER BY minuteOfDay ASC, id ASC")
    fun observeRules(): Flow<List<ScheduleRule>>

    @Query("SELECT * FROM schedule_rules ORDER BY minuteOfDay ASC, id ASC")
    suspend fun rulesOnce(): List<ScheduleRule>

    @Insert
    suspend fun insert(rule: ScheduleRule): Long

    @Update
    suspend fun update(rule: ScheduleRule)

    @Delete
    suspend fun delete(rule: ScheduleRule)

    @Query("DELETE FROM schedule_rules WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM schedule_rules WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int

    @Query("SELECT * FROM schedule_skips ORDER BY fromDay ASC, id ASC")
    fun observeSkips(): Flow<List<ScheduleSkip>>

    @Query("SELECT * FROM schedule_skips ORDER BY fromDay ASC, id ASC")
    suspend fun skipsOnce(): List<ScheduleSkip>

    @Insert
    suspend fun insert(skip: ScheduleSkip): Long

    @Delete
    suspend fun delete(skip: ScheduleSkip)

    /** Days off that are over are of no more use; dropped when the schedule screen opens. */
    @Query("DELETE FROM schedule_skips WHERE toDay < :today")
    suspend fun deleteSkipsBefore(today: Long)
}
