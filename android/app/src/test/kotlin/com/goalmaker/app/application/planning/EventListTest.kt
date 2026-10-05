package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Calendar events on a real replica (docs/calendar.md, M10-01). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EventListTest {
    private lateinit var test: TestReplica
    private lateinit var events: EventList
    private var syncs = 0
    private val monday = LocalDate.parse("2026-10-12")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-10-05T12:00:00Z") })
        events = EventList(test.replica, rows) { syncs++ }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a new event is trimmed, made by the owner, and asks for a sync`() {
        val prague = events.add(EventDraft("  Prague ", monday, monday.plusDays(3), notes = "  Hotel near the river  "))!!

        assertEquals("Prague", prague.title)
        assertEquals(monday, prague.startsOn)
        assertEquals(monday.plusDays(3), prague.endsOn)
        assertEquals("Hotel near the river", prague.notes)
        assertEquals(ProjectRules.OWNER, prague.madeBy)
        assertEquals(4, prague.days)
        assertEquals(1, syncs)
        assertEquals(prague, events.get(prague.id))
        assertNull(events.add(EventDraft("Dentist", monday, notes = "   "))!!.notes)
    }

    @Test
    fun `a draft the server would refuse is not saved`() {
        assertNull(events.add(EventDraft("   ", monday)))
        assertNull(events.add(EventDraft("x".repeat(201), monday)))
        assertNull(events.add(EventDraft("Back to front", monday, monday.minusDays(1))))
        assertNull(events.add(EventDraft("Too long", monday, monday.plusDays(367))))
        assertNull(events.add(EventDraft("Long notes", monday, notes = "n".repeat(10_001))))
        assertEquals(366, events.add(EventDraft("A year and a day", monday, monday.plusDays(366)))!!.days - 1)
        assertEquals(1, events.all().size)
    }

    @Test
    fun `an edit changes the days, the area and the notes`() {
        val prague = events.add(EventDraft("Prague", monday, monday.plusDays(3)))!!

        assertTrue(events.update(prague.id, EventDraft("Prague and Brno", monday.plusDays(7), monday.plusDays(9), "Train", "area-1")))
        assertFalse(events.update(prague.id, EventDraft("Prague", monday, monday.minusDays(2))))

        val edited = events.get(prague.id)!!
        assertEquals("Prague and Brno", edited.title)
        assertEquals(monday.plusDays(7), edited.startsOn)
        assertEquals(monday.plusDays(9), edited.endsOn)
        assertEquals("Train", edited.notes)
        assertEquals("area-1", edited.areaId)
    }

    @Test
    fun `a range holds the events that touch it, in the day order`() {
        val dentist = events.add(EventDraft("Dentist", monday.plusDays(1)))!!
        val prague = events.add(EventDraft("Prague", monday.minusDays(2), monday.plusDays(1)))!!
        events.add(EventDraft("Later", monday.plusDays(10)))

        assertEquals(listOf(prague.id, dentist.id), events.between(monday, monday.plusDays(6)).map(EventItem::id))
    }

    @Test
    fun `a deleted event leaves, and the undo brings it back`() = runTest {
        val prague = events.add(EventDraft("Prague", monday, monday.plusDays(3)))!!

        assertTrue(events.delete(prague.id))
        assertNull(events.get(prague.id))
        assertEquals(emptyList<EventItem>(), events.watch().first())
        assertFalse(events.delete(prague.id))

        assertTrue(events.restore(prague.id))
        assertEquals(listOf("Prague"), events.watch().first().map(EventItem::title))
        assertFalse(events.restore(prague.id))
    }
}
