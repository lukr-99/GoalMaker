package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import com.goalmaker.app.data.replica.TestReplica
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Life goal pictures kept here, sent up, brought down and cleared (docs/life-goals.md, ADR 0018). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LifeGoalPicturesTest {
    private lateinit var test: TestReplica
    private lateinit var lifeGoals: LifeGoalList
    private lateinit var pictures: LifeGoalPictures
    private val files = MemoryFiles()
    private val cloud = MemoryCloud()
    private var now = Instant.parse("2026-10-04T12:00:00Z")
    private var transfers = 0

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { now })
        lifeGoals = LifeGoalList(test.replica, rows, {})
        pictures = LifeGoalPictures(lifeGoals, files, cloud, { TestReplica.OWNER }, { now }) { transfers++ }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a picture is kept at once and goes up when the server can be reached`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val photo = pictures.add(car.id, JPEG, 1600, 900)!!

        assertArrayEquals(JPEG, pictures.read(photo.id))
        assertEquals(setOf(photo.id), files.pending())
        assertEquals(1, transfers)

        cloud.offline = true
        assertFalse(pictures.transfer())
        assertEquals(setOf(photo.id), files.pending())

        cloud.offline = false
        assertTrue(pictures.transfer())
        assertArrayEquals(JPEG, cloud.stored["${TestReplica.OWNER}/${photo.id}"])
        assertTrue(files.pending().isEmpty())
    }

    @Test
    fun `a picture from the other device comes down once`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val photo = lifeGoals.addPicture(car.id, 1600, 900)!!
        cloud.stored["${TestReplica.OWNER}/${photo.id}"] = JPEG

        assertTrue(pictures.transfer())
        assertTrue(pictures.transfer())

        assertArrayEquals(JPEG, pictures.read(photo.id))
        assertEquals(1, cloud.downloads)
    }

    @Test
    fun `a deleted picture's file goes a day later, so an undo still finds it`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val photo = pictures.add(car.id, JPEG, 1600, 900)!!
        assertTrue(pictures.transfer())

        assertTrue(lifeGoals.delete(car.id))
        assertTrue(pictures.transfer())
        assertArrayEquals(JPEG, pictures.read(photo.id))
        assertTrue(lifeGoals.restore(car.id))
        assertTrue(lifeGoals.delete(car.id))

        now = now.plus(Duration.ofDays(2))
        assertTrue(pictures.transfer())

        assertNull(pictures.read(photo.id))
        assertFalse(cloud.stored.containsKey("${TestReplica.OWNER}/${photo.id}"))
    }

    @Test
    fun `a picture deleted before it went up is never sent`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val photo = pictures.add(car.id, JPEG, 1600, 900)!!
        assertTrue(lifeGoals.removePicture(photo.id))

        assertTrue(pictures.transfer())

        assertTrue(cloud.stored.isEmpty())
        assertTrue(files.pending().isEmpty())
    }

    @Test
    fun `a refused file is skipped and the rest still go`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val big = pictures.add(car.id, JPEG, 1600, 900)!!
        val small = pictures.add(car.id, JPEG, 800, 450)!!
        cloud.refuse = setOf(big.id)

        assertTrue(pictures.transfer())

        assertEquals(setOf(big.id), files.pending())
        assertTrue(cloud.stored.containsKey("${TestReplica.OWNER}/${small.id}"))
    }

    private class MemoryFiles : PictureFiles {
        private val bytes = mutableMapOf<String, ByteArray>()
        private val waiting = mutableSetOf<String>()

        override fun has(id: String) = id in bytes
        override fun read(id: String) = bytes[id]
        override fun write(id: String, bytes: ByteArray, pending: Boolean) {
            this.bytes[id] = bytes
            if (pending) waiting += id
        }
        override fun delete(id: String) {
            bytes -= id
            waiting -= id
        }
        override fun ids() = bytes.keys.toSet()
        override fun pending() = waiting.toSet()
        override fun uploaded(id: String) {
            waiting -= id
        }
    }

    private class MemoryCloud : PictureCloud {
        val stored = mutableMapOf<String, ByteArray>()
        var offline = false
        var refuse = emptySet<String>()
        var downloads = 0

        override suspend fun upload(owner: String, id: String, bytes: ByteArray) {
            check()
            if (id in refuse) throw RemoteRejectedException("HTTP 413: too big", null)
            stored["$owner/$id"] = bytes
        }

        override suspend fun download(owner: String, id: String): ByteArray? {
            check()
            downloads++
            return stored["$owner/$id"]
        }

        override suspend fun remove(owner: String, id: String) {
            check()
            stored -= "$owner/$id"
        }

        private fun check() {
            if (offline) throw RemoteUnavailableException("The server can't be reached.")
        }
    }

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
