package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
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

/** Wants and their thresholds on a real replica (docs/wants.md, M8-03). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WantListTest {
    private lateinit var test: TestReplica
    private lateinit var wants: WantList
    private var day = LocalDate.parse("2026-09-28")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-28T12:00:00Z") })
        wants = WantList(test.replica, rows, {}) { day }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a new want cools as its price says and needs a reason`() {
        val shoes = wants.add(WantDraft(title = " Trail shoes ", reason = " The old ones have holes ", price = 3400.0))!!

        assertEquals("Trail shoes", shoes.title)
        assertEquals(30, shoes.cooldownDays)
        assertEquals(LocalDate.parse("2026-10-28"), shoes.coolsUntil)
        assertEquals(ProjectRules.OWNER, shoes.madeBy)
        assertNull(wants.add(WantDraft(title = "Boat", reason = "  ")))
        assertEquals(listOf("Trail shoes"), wants.all().map(WantItem::title))
    }

    @Test
    fun `the owner's own thresholds give new wants their days and leave the old ones`() {
        val before = wants.add(WantDraft(title = "Lamp", reason = "Dark desk", price = 1500.0))!!
        assertTrue(wants.setCooldowns(WantCooldowns.DEFAULT.copy(smallUnder = 2000.0, smallDays = 3)))

        val after = wants.add(WantDraft(title = "Mug", reason = "Broke mine", price = 1500.0))!!

        assertEquals(3, after.cooldownDays)
        assertEquals(30, wants.get(before.id)!!.cooldownDays)
        assertEquals(2000.0, wants.cooldowns().smallUnder, 0.0)
        assertTrue(wants.setCooldowns(wants.cooldowns().copy(smallDays = 5)))
        assertEquals(5, wants.cooldowns().smallDays)
    }

    @Test
    fun `thresholds that don't hold together are refused`() {
        assertFalse(wants.setCooldowns(WantCooldowns.DEFAULT.copy(mediumUnder = 500.0)))
        assertFalse(wants.setCooldowns(WantCooldowns.DEFAULT.copy(largeDays = 400)))
        assertFalse(wants.setCooldowns(WantCooldowns.DEFAULT.copy(currency = "crowns")))
        assertEquals(WantCooldowns.DEFAULT, wants.cooldowns())
    }

    @Test
    fun `a picked number of days wins, and a want is decided, reopened and deleted`() {
        val kindle = wants.add(WantDraft(title = "Kindle", reason = "Reading at night", price = 3290.0, pickedDays = 0))!!
        assertEquals(WantState.READY, WantRules.state(kindle, day))

        assertTrue(wants.decide(kindle.id, WantRules.DROPPED, " Library card works "))
        val dropped = wants.get(kindle.id)!!
        assertEquals(WantState.DECIDED, WantRules.state(dropped, day))
        assertEquals("Library card works", dropped.decisionNote)
        assertFalse(wants.decide(kindle.id, "maybe"))

        assertTrue(wants.reopen(kindle.id))
        assertNull(wants.get(kindle.id)!!.decision)

        assertTrue(wants.delete(kindle.id))
        assertNull(wants.get(kindle.id))
        assertFalse(wants.decide(kindle.id, WantRules.BOUGHT))
    }

    @Test
    fun `editing a want keeps its cooldown`() {
        val desk = wants.add(WantDraft(title = "Desk", reason = "Back pain", price = 12900.0))!!
        day = LocalDate.parse("2026-10-10")

        assertTrue(wants.update(desk.id, WantDraft(title = "Standing desk", reason = "Back pain", price = 900.0)))

        val edited = wants.get(desk.id)!!
        assertEquals("Standing desk", edited.title)
        assertEquals(90, edited.cooldownDays)
        assertEquals(LocalDate.parse("2026-12-27"), edited.coolsUntil)
    }
}
