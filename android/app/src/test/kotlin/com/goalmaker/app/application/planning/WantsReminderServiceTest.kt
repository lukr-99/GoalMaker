package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.planning.QuietHours
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

/** The wants notification on the shared alarm: when it rings, what it names, when it goes (M8-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WantsReminderServiceTest {
    private lateinit var test: TestReplica
    private lateinit var wants: WantList
    private lateinit var service: ReminderService
    private val armed = mutableListOf<LocalDateTime?>()
    private var now = LocalDateTime.parse("2026-09-28T12:00")
    private var remindedUntil: LocalDateTime? = null
    private var readyAt: LocalTime? = LocalTime.of(10, 0)
    private var added = LocalDate.parse("2026-09-28")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-28T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        val tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { added }
        wants = WantList(test.replica, rows, {}) { added }
        service = ReminderService(
            reminders = ReminderList(test.replica, rows, {}),
            tasks = tasks,
            scheduler = object : ReminderScheduler {
                override fun armAt(at: LocalDateTime) {
                    armed += at
                }

                override fun cancel() {
                    armed += null
                }
            },
            quietHours = { QuietHours.OFF },
            dayStartHour = { 4 },
            now = { now },
            remindedUntil = { remindedUntil },
            setRemindedUntil = { remindedUntil = it },
            wants = wants,
            wantsReadyAt = { readyAt },
        )
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `the alarm waits for the morning a want cools, and the look names it once`() {
        val lamp = wants.add(WantDraft("Lamp", "Dark desk", price = 450.0))!!
        service.rearm()
        assertEquals(LocalDateTime.parse("2026-10-05T10:00"), armed.last())

        remindedUntil = LocalDateTime.parse("2026-10-05T09:00")
        now = LocalDateTime.parse("2026-10-05T10:00:01")
        assertEquals(WantsDue(LocalDate.parse("2026-10-05"), listOf(lamp.id)), service.catchUp().wants)

        now = LocalDateTime.parse("2026-10-05T11:00")
        assertNull(service.catchUp().wants)
    }

    @Test
    fun `deciding every want it named takes the notification down`() {
        val kindle = wants.add(WantDraft("Kindle", "Reading at night", pickedDays = 0))!!
        val shoes = wants.add(WantDraft("Trail shoes", "Holes", pickedDays = 0))!!

        assertFalse(service.wantsStale(listOf(kindle.id, shoes.id)))
        wants.decide(kindle.id, WantRules.BOUGHT)
        assertFalse(service.wantsStale(listOf(kindle.id, shoes.id)))
        wants.delete(shoes.id)
        assertTrue(service.wantsStale(listOf(kindle.id, shoes.id)))
    }

    @Test
    fun `switched off, it neither rings nor arms`() {
        readyAt = null
        wants.add(WantDraft("Lamp", "Dark desk", price = 450.0))!!
        service.rearm()
        assertNull(armed.last())

        remindedUntil = LocalDateTime.parse("2026-10-05T09:00")
        now = LocalDateTime.parse("2026-10-05T10:00:01")
        assertNull(service.catchUp().wants)
    }
}
