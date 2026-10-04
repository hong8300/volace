package com.hong.volace.audio

import android.content.Context
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.data.VolaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one way to apply a profile, from anywhere (list, widgets, tile picker, edit screen).
 *
 * Taps can come faster than a profile is written: two widget cells in a row, or the 1×1 pressed
 * twice. Each switch picks its target, writes every stream and records the result before the next
 * one starts, so streams of two profiles never interleave, the last tap is the one in effect, and
 * a double tap on the 1×1 steps twice instead of applying the same "next" profile twice.
 * The work also runs off the main thread (about ten binder calls per apply).
 */
object ProfileSwitcher {

    data class Outcome(val profile: Profile, val result: ApplyResult)

    private val lock = Mutex()

    /**
     * Applies the profile with [id] as currently stored. With [onlyIfActive], does nothing unless
     * it is the one in effect (re-applying after an edit). Null when nothing was applied.
     */
    suspend fun apply(context: Context, id: Long, onlyIfActive: Boolean = false): Outcome? =
        switch(context) { dao ->
            dao.getById(id)?.takeIf { !onlyIfActive || it.isActive }
        }

    /** Steps to the profile after the one in effect, wrapping around (the 1×1 widget). */
    suspend fun cycle(context: Context): Outcome? =
        switch(context) { dao -> nextInCycle(dao.getAllOnce()) }

    /** The profile after the one in effect, wrapping; the first when none is. Null when empty. */
    internal fun nextInCycle(profiles: List<Profile>): Profile? {
        if (profiles.isEmpty()) return null
        // indexOfFirst returns -1 when nothing is active, which lands on index 0.
        return profiles[(profiles.indexOfFirst { it.isActive } + 1).mod(profiles.size)]
    }

    private suspend fun switch(context: Context, pick: suspend (ProfileDao) -> Profile?): Outcome? {
        val app = context.applicationContext
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val dao = VolaceDatabase.get(app).profileDao()
                val target = pick(dao) ?: return@withContext null
                val result = VolumeApplier(app).apply(target)
                // Only what Android accepted is recorded as in effect (see ApplyResult).
                if (result == ApplyResult.Applied) dao.applyActive(target.id)
                Outcome(target, result)
            }
        }
    }
}
