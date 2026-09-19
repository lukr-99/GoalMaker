package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
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

/** The one alarm shared by task reminders and the evening Plan tomorrow reminder (docs/reminders.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderServiceTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var rituals: RitualRunList
    private lateinit var service: ReminderService
    private val armed = mutableListOf<LocalDateTime?>()
    private var now = LocalDateTime.parse("2026-09-18T12:00")
    private var remindedUntil: LocalDateTime? = null
    private var planAt: LocalTime? = LocalTime.of(20, 0)

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, {}) { LocalDate.parse("2026-09-18") }
        rituals = RitualRunList(test.replica, rows, {})
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
            rituals = rituals,
            planTomorrowAt = { planAt },
        )
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `the evening reminder arms the alarm and shows once at its time`() {
        service.rearm()
        assertEquals(LocalDateTime.parse("2026-09-18T20:00"), armed.last())

        remindedUntil = LocalDateTime.parse("2026-09-18T19:00")
        now = LocalDateTime.parse("2026-09-18T20:00:01")
        val look = service.catchUp()
        assertEquals(LocalDate.parse("2026-09-18"), look.planTomorrow)
        assertEquals(LocalDateTime.parse("2026-09-19T20:00"), armed.last())

        now = LocalDateTime.parse("2026-09-18T20:05")
        assertNull(service.catchUp().planTomorrow)
    }

    @Test
    fun `a task reminder before the evening takes the alarm`() {
        val task = tasks.add(ComposerParser.parse("Call the bank", now))!!
        service.addAt(task.id, LocalDateTime.parse("2026-09-18T17:30"))

        assertEquals(LocalDateTime.parse("2026-09-18T17:30"), armed.last())
    }

    @Test
    fun `finishing the ritual quiets the day and takes its reminder down`() {
        now = LocalDateTime.parse("2026-09-18T21:00")
        val today = LocalDate.parse("2026-09-18")
        assertFalse(service.planTomorrowStale(today))

        service.finishPlanTomorrow(today)

        assertTrue(service.planTomorrowStale(today))
        assertEquals(setOf(today), rituals.ran(RitualRunList.PLAN_TOMORROW))
        assertEquals(LocalDateTime.parse("2026-09-19T20:00"), armed.last())
    }

    @Test
    fun `not today is recorded as skipped under the shared id`() {
        val today = LocalDate.parse("2026-09-18")

        service.skipPlanTomorrow(today)

        val row = test.replica.get("ritual_runs", RitualRunList.idOf(TestReplica.OWNER, RitualRunList.PLAN_TOMORROW, today))!!
        assertEquals("\"skipped\"", row["outcome"].toString())
    }

    @Test
    fun `switched off, nothing is armed for the evening`() {
        planAt = null

        service.rearm()

        assertNull(armed.last())
        assertNull(service.catchUp().planTomorrow)
    }
}
