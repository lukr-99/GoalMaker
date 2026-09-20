package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs contracts/vectors/stats.json, which the Windows app passes too. */
class StatsRulesContractTest {
    private val vectors = ContractFiles.load("vectors/stats.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = LocalDate.parse(text(name)!!)
    private fun JsonObject.number(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double
    private fun JsonObject.count(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int
    private fun JsonObject.flag(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.boolean ?: false
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.rows(name: String) = this[name]?.jsonArray.orEmpty().map { it.jsonObject }

    private fun state(name: String?) = when (name) {
        "done" -> TaskState.DONE
        "dropped" -> TaskState.DROPPED
        else -> TaskState.OPEN
    }

    private fun JsonObject.tasks() = rows("tasks").map { task ->
        TaskItem(
            id = task.text("id")!!,
            title = task.text("title") ?: "",
            state = state(task.text("state")),
            topPriority = false,
            createdAt = "2026-01-01T00:00:00Z",
            plannedDate = task.text("plannedDate")?.let(LocalDate::parse),
            areaId = task.text("areaId"),
            deleted = task.flag("deleted"),
            completedAt = task.text("completedAt"),
            goalId = task.text("goalId"),
            movedCount = task.count("movedCount") ?: 0,
        )
    }

    private fun JsonObject.goals() = rows("goals").map { goal ->
        GoalItem(
            id = goal.text("id")!!,
            title = goal.text("title") ?: "",
            horizon = GoalHorizon.of(goal.text("horizon"))!!,
            periodStart = goal.day("periodStart"),
            mode = goal.text("mode") ?: GoalRules.MODE_DONE,
            status = goal.text("status") ?: GoalRules.OPEN,
            target = goal.number("target"),
            unit = goal.text("unit"),
            deleted = goal.flag("deleted"),
        )
    }

    private fun JsonObject.entries() = rows("entries").mapIndexed { index, entry ->
        GoalEntryItem("e$index", entry.text("goalId")!!, LocalDate.parse("2026-01-01"), entry.number("amount")!!)
    }

    private fun JsonObject.habitData() = HabitData(
        habits = rows("habits").map { habit ->
            HabitItem(
                id = habit.text("id")!!,
                name = habit.text("name") ?: "",
                startsOn = habit.day("startsOn"),
                cadence = habit.text("cadence") ?: HabitRules.DAILY,
                weekdays = habit.count("weekdays"),
                times = habit.count("times"),
                measure = habit.text("measure") ?: HabitRules.CHECK,
                target = habit.number("target"),
                unit = habit.text("unit"),
                goalId = habit.text("goalId"),
                archived = habit.flag("archived"),
            )
        },
        checkins = rows("checkins").mapIndexed { index, checkin ->
            HabitCheckin(
                id = "c$index",
                habitId = checkin.text("habitId")!!,
                day = checkin.day("day"),
                value = checkin.number("value")!!,
                skipped = checkin.flag("skipped"),
            )
        },
        pauses = rows("pauses").mapIndexed { index, pause ->
            HabitPause("p$index", pause.text("habitId")!!, pause.day("from"), pause.text("until")?.let(LocalDate::parse))
        },
    )

    @Test
    fun `every week of finished tasks`() {
        vectors.cases("weeks").forEach { case ->
            val name = case.text("name")
            val weeks = StatsRules.weeks(case.tasks(), case.day("today"), case.count("count")!!)
            val expect = case.rows("expect")
            assertEquals(name, expect.size, weeks.size)
            expect.forEachIndexed { index, week ->
                assertEquals("$name week ${week.text("start")}", week.day("start"), weeks[index].start)
                assertEquals("$name week ${week.text("start")}", week.count("done"), weeks[index].done)
            }
            val digest = StatsDigest(weeks = weeks)
            assertEquals("$name total", case.count("done"), digest.done)
            assertEquals("$name best", case.text("best")?.let(LocalDate::parse), digest.bestWeek?.start)
        }
    }

    @Test
    fun `every month of goals`() {
        vectors.cases("months").forEach { case ->
            val name = case.text("name")
            val months = StatsRules.months(case.goals(), case.tasks(), case.entries(), case.habitData(), case.day("today"), case.count("count")!!)
            val expect = case.rows("expect")
            assertEquals(name, expect.size, months.size)
            expect.forEachIndexed { index, month ->
                assertEquals("$name month ${month.text("start")}", month.day("start"), months[index].start)
                assertEquals("$name hit ${month.text("start")}", month.count("hit"), months[index].hit)
                assertEquals("$name total ${month.text("start")}", month.count("total"), months[index].total)
            }
            val digest = StatsDigest(months = months)
            assertEquals("$name hit", case.count("hit"), digest.goalsHit)
            assertEquals("$name total", case.count("total"), digest.goalsTotal)
        }
    }

    @Test
    fun `every habit over the window`() {
        vectors.cases("habits").forEach { case ->
            val name = case.text("name")
            val rows = StatsRules.habits(case.habitData(), case.day("today"), case.count("weeks")!!)
            val expect = case.rows("expect")
            assertEquals(name, expect.size, rows.size)
            expect.forEachIndexed { index, habit ->
                assertEquals("$name id", habit.text("id"), rows[index].id)
                assertEquals("$name met", habit.count("met"), rows[index].met)
                assertEquals("$name periods", habit.count("periods"), rows[index].periods)
                assertEquals("$name streak", habit.count("streak"), rows[index].streak)
                assertEquals("$name best", habit.count("best"), rows[index].best)
            }
        }
    }

    @Test
    fun `every chart of ratings`() {
        vectors.cases("ratings").forEach { case ->
            val name = case.text("name")
            val reviews = case.rows("reviews").mapIndexed { index, review ->
                ReviewItem(
                    id = "r$index",
                    kind = review.text("kind")!!,
                    periodStart = review.day("periodStart"),
                    mood = review.count("mood"),
                    energy = review.count("energy"),
                    deleted = review.flag("deleted"),
                )
            }
            val ratings = StatsRules.ratings(reviews, case.text("kind")!!, case.count("count")!!)
            val expect = case.rows("expect")
            assertEquals(name, expect.size, ratings.size)
            expect.forEachIndexed { index, rating ->
                assertEquals("$name period", rating.day("periodStart"), ratings[index].periodStart)
                assertEquals("$name mood", rating.count("mood"), ratings[index].mood)
                assertEquals("$name energy", rating.count("energy"), ratings[index].energy)
            }
        }
    }

    @Test
    fun `every look back`() {
        vectors.cases("lookBack").forEach { case ->
            val name = case.text("name")
            val kind = case.text("kind")!!
            val periodStart = case.day("periodStart")
            val today = case.day("today")
            val areas = case.rows("areas").map { AreaItem(it.text("id")!!, it.text("name")!!, "blue", null) }
            val digest = ReviewLookBack.build(kind, periodStart, case.tasks(), areas, case.goals(), case.entries(), case.habitData(), today)
            val expect = case.getValue("expect").jsonObject

            assertEquals("$name end", expect.day("periodEnd"), digest.periodEnd)
            assertEquals("$name done", expect.count("done"), digest.done)
            assertEquals("$name done before", expect.count("doneBefore"), digest.doneBefore)
            assertEquals("$name change", expect.count("change"), digest.change)

            val days = expect.getValue("days").jsonObject
            assertEquals("$name days", days.count("count"), digest.days.size)
            val byDay = days.getValue("done").jsonObject
            digest.days.forEach { day ->
                assertEquals("$name ${day.day}", byDay[day.day.toString()]?.jsonPrimitive?.int ?: 0, day.done)
            }

            val bestDay = expect["bestDay"]?.takeUnless { it == JsonNull }?.jsonObject
            if (bestDay == null) {
                assertNull("$name best day", digest.bestDay)
            } else {
                assertNotNull("$name best day", digest.bestDay)
                assertEquals("$name best day", bestDay.day("day"), digest.bestDay!!.day)
                assertEquals("$name best day", bestDay.count("done"), digest.bestDay!!.done)
            }

            val area = expect["strongestArea"]?.takeUnless { it == JsonNull }?.jsonObject
            if (area == null) {
                assertNull("$name area", digest.strongestArea)
            } else {
                assertEquals("$name area", area.text("name"), digest.strongestArea?.name)
                assertEquals("$name area", area.count("done"), digest.strongestArea?.done)
            }

            val goals = expect.rows("goals")
            assertEquals("$name goals", goals.size, digest.goals.size)
            goals.forEachIndexed { index, goal ->
                assertEquals("$name goal", goal.text("id"), digest.goals[index].id)
                assertEquals("$name goal fraction", goal.number("fraction")!!, digest.goals[index].fraction, 1e-9)
                assertEquals("$name goal hit", goal.flag("hit"), digest.goals[index].hit)
            }

            val habits = expect.rows("habits")
            assertEquals("$name habits", habits.size, digest.habits.size)
            habits.forEachIndexed { index, habit ->
                assertEquals("$name habit", habit.text("id"), digest.habits[index].id)
                assertEquals("$name habit met", habit.count("met"), digest.habits[index].met)
                assertEquals("$name habit periods", habit.count("periods"), digest.habits[index].periods)
                assertEquals("$name habit streak", habit.count("streak"), digest.habits[index].streak)
            }

            assertEquals(
                "$name open",
                expect.getValue("openTasks").jsonArray.map { it.jsonPrimitive.content },
                digest.openTasks.map(TaskItem::id),
            )

            val expected = expect.number("expected")!!
            assertEquals("$name expected", expected, ReviewLookBack.expected(kind, periodStart, digest.periodEnd, today), 1e-9)

            val facts = expect.getValue("facts").jsonObject
            assertEquals("$name facts done", facts.count("doneTasks"), digest.facts.doneTasks)
            assertEquals("$name facts average", facts.number("averageDone")!!, digest.facts.averageDone, 1e-9)
            assertEquals(
                "$name facts moves",
                facts.getValue("taskMoves").jsonArray.map { it.jsonPrimitive.int },
                digest.facts.tasks.map { it.moves },
            )
            assertEquals(
                "$name facts missed",
                facts.getValue("habitsMissed").jsonArray.map { it.jsonPrimitive.int },
                digest.facts.habits.map { it.missed },
            )
            digest.facts.goals.forEach { goal -> assertEquals("$name fact expected", expected, goal.expected, 1e-9) }
        }
    }
}
