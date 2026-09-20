package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every list that writes rows fills the columns the server needs a value in
 * (contracts/schemas/synced-tables.json). A row carries every column, so a null in one of those is
 * refused on the push whatever default the column has, and the apps' own tests never meet a server.
 * This walks all fifteen synced tables, so a table nobody writes here is caught too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RequiredColumnsTest {
    private val day: LocalDate = LocalDate.parse("2026-09-18")
    private lateinit var test: TestReplica
    private lateinit var rows: NewRows

    @Before
    fun setUp() {
        test = TestReplica()
        rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `every list writes rows the server can take`() {
        val areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        val tags = TagList(test.replica, rows, {})
        val steps = StepList(test.replica, rows, {})
        val goals = GoalList(test.replica, rows, {})
        val habits = HabitList(test.replica, rows, {})
        val reviews = ReviewList(test.replica, rows, {})
        val rituals = RitualRunList(test.replica, rows, {})
        val projects = ProjectList(test.replica, rows, {})
        val reminders = ReminderList(test.replica, rows, {})
        val tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { day }

        // One row in every synced table, each through the list that owns it.
        assertNotNull(areas.create("Health"))
        assertNotNull(tags.findOrCreate("errand"))
        val task = tasks.add(ComposerParser.parse("Call the bank 17:00 #errand @Health", LocalDateTime.parse("2026-09-18T09:00")))
        assertNotNull(task)
        assertNotNull(steps.add(task!!.id, "Find the number"))
        assertNotNull(reminders.addAt(task.id, LocalDateTime.parse("2026-09-18T16:45")))
        val goal = goals.add(GoalDraft("Run 20 km", GoalHorizon.MONTH, day, GoalRules.MODE_NUMBER, target = 20.0, unit = "km"))
        assertNotNull(goal)
        assertNotNull(goals.logAmount(goal!!.id, day, 5.0))
        val habit = habits.add(HabitDraft("Read before bed", day))
        assertNotNull(habit)
        assertNotNull(habits.checkIn(habit!!.id, day))
        assertTrue(habits.pause(habit.id, day))
        val project = projects.add(ProjectDraft("GoalMaker"))
        assertNotNull(project)
        assertNotNull(projects.addMilestone(project!!.id, "M6"))
        rituals.record(RitualRunList.PLAN_TOMORROW, day)
        assertNotNull(reviews.open(ReviewRules.WEEKLY, day))

        // Every table was written, and every row of every table fills what the server needs.
        val written = test.catalog.tables.associate { table -> table.name to test.replica.all(table.name) }
        val empty = written.filterValues(List<JsonObject>::isEmpty).keys
        assertEquals("every synced table needs a row here", emptySet<String>(), empty)
        written.forEach { (table, stored) ->
            val required = test.catalog[table].required
            assertTrue("$table describes no required column", required.isNotEmpty())
            stored.forEach { row ->
                required.forEach { column ->
                    assertTrue(
                        "$table.$column needs a value, or the server refuses the row",
                        row[column].let { it != null && it != JsonNull },
                    )
                }
            }
        }
    }
}
