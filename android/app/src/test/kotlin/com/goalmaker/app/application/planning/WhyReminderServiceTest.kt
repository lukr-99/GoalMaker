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

/** The why reminder on the shared alarm: when it rings, which life goal it names, when it goes (M9-04). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WhyReminderServiceTest {
    private lateinit var test: TestReplica
    private lateinit var lifeGoals: LifeGoalList
    private lateinit var service: ReminderService
    private val armed = mutableListOf<LocalDateTime?>()
    private var now = LocalDateTime.parse("2026-10-05T00:30")
    private var remindedUntil: LocalDateTime? = null
    private var frequency = WhyFrequency.WEEKLY
    private var quiet = QuietHours.OFF

    // The week of Monday 5 October 2026 and its worked-out moment, from the same rules the vectors pin.
    private val week = LocalDate.parse("2026-10-05")
    private val moment get() = WhyReminder.moment(WhyFrequency.WEEKLY, week, quiet)

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-10-04T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        val tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { week }
        lifeGoals = LifeGoalList(test.replica, rows, {})
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
            quietHours = { quiet },
            dayStartHour = { 4 },
            now = { now },
            remindedUntil = { remindedUntil },
            setRemindedUntil = { remindedUntil = it },
            lifeGoals = lifeGoals,
            whyFrequency = { frequency },
        )
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `the alarm waits for the week's moment, and the look names the week's life goal once`() {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        service.catchUp()
        assertEquals(moment.at, armed.last())

        now = moment.at.plusMinutes(1)
        val look = service.catchUp()

        assertEquals(WhyDue(week, car.id), look.why)
        assertNull(service.catchUp().why)
    }

    @Test
    fun `quiet hours hold it back like an ordinary reminder`() {
        lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        val plain = moment.at.toLocalTime()
        quiet = QuietHours(plain.minusMinutes(10), plain.plusMinutes(20))

        service.catchUp()

        assertEquals(moment.at.toLocalDate().atTime(plain.plusMinutes(20)), armed.last())
    }

    @Test
    fun `off rings nothing, and nothing open arms nothing`() {
        frequency = WhyFrequency.OFF
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        service.catchUp()
        assertEquals(null, armed.last())
        now = moment.at.plusMinutes(1)
        assertNull(service.catchUp().why)

        frequency = WhyFrequency.WEEKLY
        lifeGoals.achieve(car.id)
        now = LocalDateTime.parse("2026-10-05T00:30")
        remindedUntil = null
        service.catchUp()
        assertEquals(null, armed.last())
        now = moment.at.plusMinutes(1)
        assertNull(service.catchUp().why)
    }

    @Test
    fun `a moment missed while the device was off shows at the next look`() {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        service.catchUp()

        now = moment.at.plusDays(2)
        assertEquals(car.id, service.catchUp().why?.lifeGoalId)
    }

    @Test
    fun `the reminder goes once its life goal is achieved`() {
        val car = lifeGoals.add(LifeGoalDraft("Own an Audi R8", "Proof"))!!
        assertFalse(service.whyStale(car.id))

        lifeGoals.achieve(car.id)

        assertTrue(service.whyStale(car.id))
        assertTrue(service.whyStale("gone"))
    }
}
