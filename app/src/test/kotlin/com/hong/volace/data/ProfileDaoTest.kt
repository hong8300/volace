package com.hong.volace.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileDaoTest {

    private lateinit var db: VolaceDatabase
    private lateinit var dao: ProfileDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VolaceDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.profileDao()
    }

    @After
    fun close() = db.close()

    private fun profile(name: String, order: Int, active: Boolean = false) = Profile(
        name = name,
        orderIndex = order,
        ringerMode = 2,
        ringVolume = 5,
        notificationVolume = 5,
        mediaVolume = 10,
        alarmVolume = 5,
        voiceCallVolume = 9,
        systemVolume = 5,
        isActive = active,
    )

    private suspend fun activeNames() = dao.getAllOnce().filter { it.isActive }.map { it.name }

    /** #6: a save from an edit screen opened before another profile was applied. */
    @Test
    fun saveEdits_keepsWhicheverProfileIsActive() = runBlocking {
        dao.insertAll(listOf(profile("A", 0, active = true), profile("B", 1)))
        val staleCopy = dao.getAllOnce().first { it.name == "A" } // edit screen opened on A
        dao.applyActive(dao.getAllOnce().first { it.name == "B" }.id) // widget applies B

        dao.saveEdits(staleCopy.copy(mediaVolume = 3).edits())

        assertEquals(listOf("B"), activeNames())
        assertEquals(3, dao.getById(staleCopy.id)!!.mediaVolume)
    }

    @Test
    fun saveEdits_keepsTheOrder() = runBlocking {
        dao.insertAll(listOf(profile("A", 0), profile("B", 1)))
        val a = dao.getAllOnce().first { it.name == "A" }
        dao.move(a.id, 1) // A is now last

        dao.saveEdits(a.copy(name = "A2").edits()) // copy still says orderIndex 0

        assertEquals(listOf("B", "A2"), dao.getAllOnce().map { it.name })
    }

    /** #15: a gap left by deleting must not put the new profile in the middle. */
    @Test
    fun nextOrderIndex_isAfterTheLast_evenWithGaps() = runBlocking {
        assertEquals(0, dao.nextOrderIndex())
        dao.insertAll(listOf(profile("A", 0), profile("B", 1), profile("C", 2), profile("D", 3)))
        dao.getAllOnce().filter { it.name in setOf("B", "C") }.forEach { dao.delete(it) }

        val next = dao.nextOrderIndex()
        dao.insert(profile("E", next))

        assertEquals(listOf("A", "D", "E"), dao.getAllOnce().map { it.name })
    }

    @Test
    fun insertIfEmpty_onlyFillsAnEmptyTable() = runBlocking {
        dao.insertIfEmpty(listOf(profile("A", 0)))
        dao.insertIfEmpty(listOf(profile("X", 0), profile("Y", 1)))

        assertEquals(listOf("A"), dao.getAllOnce().map { it.name })
    }

    @Test
    fun applyActive_leavesExactlyOne() = runBlocking {
        dao.insertAll(listOf(profile("A", 0, active = true), profile("B", 1), profile("C", 2)))
        dao.applyActive(dao.getAllOnce().first { it.name == "C" }.id)

        assertEquals(listOf("C"), activeNames())
    }
}
