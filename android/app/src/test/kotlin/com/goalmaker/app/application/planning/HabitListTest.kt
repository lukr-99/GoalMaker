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

/** Habits, check-ins and pauses on a real replica (docs/habits.md, M4-04). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HabitListTest {
    private lateinit var test: TestReplica
    private lateinit var habits: HabitList
    private lateinit var goals: GoalList

    private val today = LocalDate.parse("2026-09-18")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        habits = HabitList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a habit keeps only the fields its cadence and measure need`() {
        val daily = habits.add(HabitDraft("Read", today, weekdays = 21, times = 3, emoji = " 📖 "))!!
        val weekly = habits.add(HabitDraft("Run", today, HabitRules.PER_WEEK, times = 3, measure = HabitRules.AMOUNT, target = 5.0, unit = " KM "))!!

        assertEquals("Read", daily.name)
        assertEquals("📖", daily.emoji)
        assertNull(daily.weekdays)
        assertNull(daily.times)
        assertNull(daily.target)
        assertEquals(3, weekly.times)
        assertEquals(5.0, weekly.target!!, 1e-9)
        assertEquals("KM", weekly.unit)
    }

    @Test
    fun `a cadence without its days, a count without a target and a blank name are refused`() {
        assertNull(habits.add(HabitDraft("Gym", today, HabitRules.WEEKDAYS)))
        assertNull(habits.add(HabitDraft("Gym", today, HabitRules.PER_WEEK, times = 9)))
        assertNull(habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT)))
        assertNull(habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 0.0)))
        assertNull(habits.add(HabitDraft("  ", today)))
        assertTrue(habits.all().isEmpty())
    }

    @Test
    fun `a day has one check-in, and tapping again adds to a count`() {
        val water = habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0, unit = "glasses"))!!

        assertEquals(1.0, habits.checkIn(water.id, today)!!, 1e-9)
        assertEquals(3.0, habits.checkIn(water.id, today, 2.0)!!, 1e-9)

        val checkins = habits.read().checkinsOf(water.id)
        assertEquals(1, checkins.size)
        assertEquals(HabitRules.checkinId(water.id, today), checkins.single().id)
        assertEquals(3.0, checkins.single().value, 1e-9)
    }

    @Test
    fun `a tap checks and unchecks a check habit`() {
        val read = habits.add(HabitDraft("Read", today))!!

        assertTrue(habits.tap(read.id, today))
        assertEquals(1.0, habits.read().checkinsOf(read.id).single().value, 1e-9)
        assertTrue(habits.tap(read.id, today))
        assertEquals(0.0, habits.read().checkinsOf(read.id).single().value, 1e-9)
    }

    @Test
    fun `an amount asks for its value instead of one tap`() {
        val run = habits.add(HabitDraft("Run", today, measure = HabitRules.AMOUNT, target = 5.0, unit = "km"))!!

        assertFalse(habits.tap(run.id, today))
        assertTrue(habits.read().checkinsOf(run.id).isEmpty())
        assertEquals(5.0, habits.checkIn(run.id, today, 5.0)!!, 1e-9)
    }

    @Test
    fun `a skip clears the day's value and can be taken back`() {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.checkIn(read.id, today)

        assertTrue(habits.skip(read.id, today))
        habits.read().checkinsOf(read.id).single().let { checkin ->
            assertTrue(checkin.skipped)
            assertEquals(0.0, checkin.value, 1e-9)
        }

        assertTrue(habits.skip(read.id, today, skipped = false))
        assertFalse(habits.read().checkinsOf(read.id).single().skipped)
    }

    @Test
    fun `a pause ends the day before the habit resumes, and one resumed the same day goes away`() {
        val read = habits.add(HabitDraft("Read", today))!!

        assertTrue(habits.pause(read.id, today.minusDays(3)))
        assertFalse(habits.pause(read.id, today))
        assertTrue(habits.resume(read.id, today))
        assertEquals(today.minusDays(1), habits.read().pausesOf(read.id).single().until)

        assertTrue(habits.pause(read.id, today))
        assertTrue(habits.resume(read.id, today))
        assertEquals(1, habits.read().pausesOf(read.id).size)
    }

    @Test
    fun `an archived habit stays with its history, and a deleted one goes`() {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.checkIn(read.id, today)

        assertTrue(habits.setArchived(read.id, true))
        assertTrue(habits.all().single().archived)
        assertTrue(habits.setArchived(read.id, false))
        assertFalse(habits.all().single().archived)

        assertTrue(habits.delete(read.id))
        assertTrue(habits.all().isEmpty())
        assertNull(habits.find(read.id))
    }

    @Test
    fun `check-ins of a habit in the goal's unit count toward it`() {
        val goal = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, today, GoalRules.MODE_NUMBER, target = 80.0, unit = "km"))!!
        val run = habits.add(HabitDraft("Run", today, measure = HabitRules.AMOUNT, target = 5.0, unit = "KM", goalId = goal.id))!!
        val other = habits.add(HabitDraft("Read", today, measure = HabitRules.AMOUNT, target = 20.0, unit = "minutes", goalId = goal.id))!!
        assertNotNull(habits.checkIn(run.id, today, 6.0))
        assertNotNull(habits.checkIn(run.id, today.minusDays(1), 4.0))
        assertNotNull(habits.checkIn(other.id, today, 30.0))

        val data = habits.read()
        assertEquals(listOf(4.0, 6.0), HabitRules.goalAmounts(goal, data.habits, data.checkins).sorted())
    }
}
