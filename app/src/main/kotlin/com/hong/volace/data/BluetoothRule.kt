package com.hong.volace.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * "When [address] connects, switch to [profileId]; when it disconnects, do [onDisconnect]"
 * (DESIGN.md 5.18). One rule per device. No foreign key, for the same reason as ScheduleRule.
 */
@Entity(tableName = "bluetooth_rules", indices = [Index(value = ["address"], unique = true)])
data class BluetoothRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The device's MAC address, as BluetoothDevice.getAddress() gives it. */
    val address: String,
    /** The device's name when the rule was made, for when Bluetooth access is not granted. */
    val name: String,
    val profileId: Long,
    /** [DISCONNECT_RESTORE], [DISCONNECT_PROFILE] or [DISCONNECT_NOTHING]. */
    val onDisconnect: Int = DISCONNECT_RESTORE,
    /** The profile for [DISCONNECT_PROFILE]. */
    val disconnectProfileId: Long? = null,
    val enabled: Boolean = true,
) {
    companion object {
        /** Back to the state from before the device connected. */
        const val DISCONNECT_RESTORE = 0
        const val DISCONNECT_PROFILE = 1
        /** Leave the profile in effect. */
        const val DISCONNECT_NOTHING = 2
    }
}

@Dao
interface BluetoothRuleDao {
    @Query("SELECT * FROM bluetooth_rules ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<BluetoothRule>>

    @Query("SELECT * FROM bluetooth_rules ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAllOnce(): List<BluetoothRule>

    @Query("SELECT * FROM bluetooth_rules WHERE address = :address")
    suspend fun forAddress(address: String): BluetoothRule?

    @Insert
    suspend fun insert(rule: BluetoothRule): Long

    @Update
    suspend fun update(rule: BluetoothRule)

    @Delete
    suspend fun delete(rule: BluetoothRule)

    @Query("DELETE FROM bluetooth_rules WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    /** A deleted profile chosen for the disconnect: back to the state from before instead. */
    @Query("UPDATE bluetooth_rules SET onDisconnect = 0, disconnectProfileId = NULL WHERE disconnectProfileId = :profileId")
    suspend fun forgetDisconnectProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM bluetooth_rules WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int
}
