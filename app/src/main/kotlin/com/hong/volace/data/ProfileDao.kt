package com.hong.volace.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY orderIndex ASC, id ASC")
    suspend fun getAllOnce(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: Profile): Long

    @Insert
    suspend fun insertAll(profiles: List<Profile>)

    @Update
    suspend fun update(profile: Profile)

    @Update
    suspend fun updateAll(profiles: List<Profile>)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Transaction
    suspend fun applyActive(id: Long) {
        clearActive()
        setActive(id)
    }

    /**
     * Moves the profile at [from] to [to] and rewrites every orderIndex so the list stays a dense
     * 0..n-1 sequence. Cheap enough for the handful of profiles this app holds.
     */
    @Transaction
    suspend fun move(from: Long, delta: Int) {
        val ordered = getAllOnce().toMutableList()
        val index = ordered.indexOfFirst { it.id == from }
        if (index < 0) return
        val target = index + delta
        if (target !in ordered.indices) return
        ordered.add(target, ordered.removeAt(index))
        updateAll(ordered.mapIndexed { i, profile -> profile.copy(orderIndex = i) })
    }
}
