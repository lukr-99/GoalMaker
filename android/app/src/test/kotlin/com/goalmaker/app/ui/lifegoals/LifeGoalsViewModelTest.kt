package com.goalmaker.app.ui.lifegoals

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.LifeGoalDraft
import com.goalmaker.app.application.planning.LifeGoalList
import com.goalmaker.app.application.planning.LifeGoalPictures
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.PictureCloud
import com.goalmaker.app.application.planning.PictureFiles
import com.goalmaker.app.application.planning.TimeLeft
import com.goalmaker.app.application.planning.TimeLeftUnit
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Life goals place over a real replica: order, time left, the editor's pictures, undo (M9-02). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LifeGoalsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var lifeGoals: LifeGoalList
    private lateinit var viewModel: LifeGoalsViewModel
    private val files = MemoryFiles()

    // Sunday 4 October 2026 at 02:00: with the day starting at 04:00 it is still Saturday the 3rd.
    private val now = LocalDateTime.parse("2026-10-04T02:00")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-10-04T00:00:00Z") })
        lifeGoals = LifeGoalList(test.replica, rows, {})
        val pictures = LifeGoalPictures(lifeGoals, files, NoCloud, { TestReplica.OWNER }, Instant::now) {}
        viewModel = LifeGoalsViewModel(lifeGoals, pictures, MutableStateFlow(4), Dispatchers.Unconfined) { now }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `open life goals come in order with their time left, closed ones apart`() = runTest {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof", LocalDate.parse("2036-10-04")))!!
        val run = lifeGoals.add(LifeGoalDraft("Run a marathon", "To know I can"))!!
        val boat = lifeGoals.add(LifeGoalDraft("Sail to Greece", "The sea"))!!
        lifeGoals.drop(boat.id)

        val state = viewModel.uiState.first { it.loaded && it.closed.isNotEmpty() }

        assertEquals(listOf(car.id, run.id), state.open.map { it.goal.id })
        // The planning day is still the 3rd, so the 4th in ten years is ten years and a day away.
        assertEquals(TimeLeft(TimeLeftUnit.YEARS, 10), state.open.first().timeLeft)
        assertEquals(null, state.open.last().timeLeft)
        assertEquals(listOf(boat.id), state.closed.map { it.goal.id })
        assertEquals(LocalDate.parse("2026-10-03"), viewModel.today())
    }

    @Test
    fun `saving the editor keeps the life goal and the pictures it added, and drops the removed ones`() = runTest {
        val jpeg = byteArrayOf(1, 2, 3)
        val saved = viewModel.save(null, LifeGoalDraft("Own an Audi R8", "Proof"), listOf(ShrunkPicture(jpeg, 1600, 900)), emptySet())
        assertNotNull(saved)
        val first = lifeGoals.pictures(saved!!.id).single()
        assertArrayEquals(jpeg, viewModel.picture(first.id))

        val edited = viewModel.save(saved, LifeGoalDraft("Own an Audi R8 Spyder", "Proof"), listOf(ShrunkPicture(jpeg, 800, 450)), setOf(first.id))

        assertEquals("Own an Audi R8 Spyder", edited?.let { lifeGoals.get(it.id)!!.title })
        assertEquals(listOf(800), lifeGoals.pictures(saved.id).map { it.width })
        assertEquals(null, viewModel.save(null, LifeGoalDraft("No why", " "), emptyList(), emptySet()))
    }

    @Test
    fun `achieving offers an undo that reopens it, and moving swaps neighbours`() = runTest {
        val a = lifeGoals.add(LifeGoalDraft("A", "a"))!!
        val b = lifeGoals.add(LifeGoalDraft("B", "b"))!!
        val undo = mutableListOf<LifeGoalUndo>()
        val collecting = CoroutineScope(Dispatchers.Unconfined).launch { viewModel.undo.collect { undo += it } }
        val watching = CoroutineScope(Dispatchers.Unconfined).launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.open.size == 2 }

        viewModel.move(b, -1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(b.id, a.id), lifeGoals.all().map { it.id })

        viewModel.achieve(a)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LifeGoalRules.ACHIEVED, lifeGoals.get(a.id)!!.status)
        assertEquals(LifeGoalUndo.Kind.ACHIEVED, undo.single().kind)
        undo.single().undo()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(lifeGoals.get(a.id)!!.status == LifeGoalRules.OPEN)
        collecting.cancel()
        watching.cancel()
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

    private object NoCloud : PictureCloud {
        override suspend fun upload(owner: String, id: String, bytes: ByteArray) = Unit
        override suspend fun download(owner: String, id: String): ByteArray? = null
        override suspend fun remove(owner: String, id: String) = Unit
    }
}
