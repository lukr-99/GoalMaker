package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Life goals and their pictures' rows on a real replica (docs/life-goals.md, M9-01). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LifeGoalListTest {
    private lateinit var test: TestReplica
    private lateinit var lifeGoals: LifeGoalList
    private var now = Instant.parse("2026-10-04T12:00:00Z")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { now })
        lifeGoals = LifeGoalList(test.replica, rows, {})
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a new life goal is open, last, and needs a why`() {
        val car = lifeGoals.add(LifeGoalDraft(" Own an Audi R8 ", " Proof that the work paid off ", LocalDate.parse("2036-10-04")))!!
        val run = lifeGoals.add(LifeGoalDraft("Run a marathon", "To know I can"))!!

        assertEquals("Own an Audi R8", car.title)
        assertEquals("Proof that the work paid off", car.why)
        assertEquals(LocalDate.parse("2036-10-04"), car.by)
        assertEquals(LifeGoalRules.OPEN, car.status)
        assertEquals(ProjectRules.OWNER, car.madeBy)
        assertTrue(run.position > car.position)
        assertNull(lifeGoals.add(LifeGoalDraft("Boat", "  ")))
        assertEquals(listOf("Own an Audi R8", "Run a marathon"), lifeGoals.all().map(LifeGoalItem::title))
    }

    @Test
    fun `achieved and dropped ones go below the open ones, and reopen`() {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val run = lifeGoals.add(LifeGoalDraft("Run a marathon", "To know I can"))!!
        val boat = lifeGoals.add(LifeGoalDraft("Sail to Greece", "The sea"))!!

        assertTrue(lifeGoals.achieve(car.id))
        now = now.plusSeconds(60)
        assertTrue(lifeGoals.drop(boat.id))

        assertEquals(listOf(run.id, boat.id, car.id), lifeGoals.all().map(LifeGoalItem::id))
        assertNotNull(lifeGoals.get(car.id)!!.closedAt)
        assertTrue(lifeGoals.reopen(car.id))
        assertEquals(LifeGoalRules.OPEN, lifeGoals.get(car.id)!!.status)
        assertNull(lifeGoals.get(car.id)!!.closedAt)
    }

    @Test
    fun `the owner orders them`() {
        val a = lifeGoals.add(LifeGoalDraft("A", "a"))!!
        val b = lifeGoals.add(LifeGoalDraft("B", "b"))!!
        val c = lifeGoals.add(LifeGoalDraft("C", "c"))!!

        assertTrue(lifeGoals.reorder(listOf(c.id, a.id, b.id)))

        assertEquals(listOf("C", "A", "B"), lifeGoals.all().map(LifeGoalItem::title))
        assertFalse(lifeGoals.reorder(listOf("nope")))
    }

    @Test
    fun `pictures keep their order and go with their life goal, and come back with it`() {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val front = lifeGoals.addPicture(car.id, 1600, 900)!!
        val side = lifeGoals.addPicture(car.id, 1600, 1067)!!
        val old = lifeGoals.addPicture(car.id, 800, 600)!!
        assertTrue(lifeGoals.removePicture(old.id))
        assertNull(lifeGoals.addPicture(car.id, 0, 900))
        assertNull(lifeGoals.addPicture("missing", 1600, 900))

        assertTrue(lifeGoals.reorderPictures(car.id, listOf(side.id, front.id)))
        assertEquals(listOf(side.id, front.id), lifeGoals.pictures(car.id).map(LifeGoalPicture::id))

        now = now.plusSeconds(60)
        assertTrue(lifeGoals.delete(car.id))
        assertNull(lifeGoals.get(car.id))
        assertTrue(lifeGoals.allPictures().isEmpty())

        assertTrue(lifeGoals.restore(car.id))
        assertEquals(car.id, lifeGoals.get(car.id)!!.id)
        assertEquals(listOf(side.id, front.id), lifeGoals.pictures(car.id).map(LifeGoalPicture::id))
    }
}
