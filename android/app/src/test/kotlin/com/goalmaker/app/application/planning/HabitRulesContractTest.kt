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
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs contracts/vectors/habits.json, which the Windows app and the connector pass too. */
class HabitRulesContractTest {
    private val vectors = ContractFiles.load("vectors/habits.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = LocalDate.parse(text(name)!!)
    private fun JsonObject.number(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.habit(): HabitItem {
        val habit = getValue("habit").jsonObject
        return HabitItem(
            id = "h",
            name = "Habit",
            startsOn = habit.day("startsOn"),
            cadence = habit.text("cadence")!!,
            weekdays = habit["weekdays"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
            times = habit["times"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
            measure = habit.text("measure")!!,
            target = habit.number("target"),
        )
    }

    private fun JsonObject.checkins() = getValue("checkins").jsonArray.mapIndexed { index, element ->
        val checkin = element.jsonObject
        HabitCheckin(
            id = "c$index",
            habitId = checkin.text("habitId") ?: "h",
            day = checkin.day("day"),
            value = checkin.number("value")!!,
            skipped = checkin.getValue("skipped").jsonPrimitive.boolean,
        )
    }

    private fun JsonObject.pauses() = getValue("pauses").jsonArray.mapIndexed { index, element ->
        val pause = element.jsonObject
        HabitPause("p$index", "h", pause.day("from"), pause.text("until")?.let(LocalDate::parse))
    }

    @Test
    fun `every due day`() {
        vectors.cases("due").forEach { case ->
            assertEquals(case.text("name"), case.getValue("expect").jsonPrimitive.boolean, HabitRules.isDue(case.habit(), case.day("day")))
        }
    }

    @Test
    fun `every period`() {
        vectors.cases("periods").forEach { case ->
            val habit = case.habit()
            val start = HabitRules.periodStart(habit, case.day("day"))
            assertEquals("${habit.cadence} ${case.text("day")}", case.day("start"), start)
            assertEquals("${habit.cadence} ${case.text("day")} end", case.day("end"), HabitRules.periodEnd(habit, start))
        }
    }

    @Test
    fun `every state`() {
        vectors.cases("states").forEach { case ->
            val state = HabitRules.state(case.habit(), case.day("period"), case.day("today"), case.checkins(), case.pauses())
            assertEquals(case.text("name"), case.text("expect"), state.id)
        }
    }

    @Test
    fun `every streak`() {
        vectors.cases("streaks").forEach { case ->
            val streak = HabitRules.streak(case.habit(), case.day("today"), case.checkins(), case.pauses())
            assertEquals(case.text("name"), case.getValue("expect").jsonPrimitive.int, streak)
        }
    }

    @Test
    fun `every heat`() {
        vectors.cases("heat").forEach { case ->
            val heat = HabitRules.heat(case.habit(), case.day("day"), case.checkins(), case.pauses())
            val expect = case.getValue("expect")
            val name = case.text("name")
            when {
                expect == JsonNull -> assertEquals(name, HabitHeat.None, heat)
                expect.jsonPrimitive.isString -> assertEquals(
                    name,
                    if (expect.jsonPrimitive.content == "paused") HabitHeat.Paused else HabitHeat.Skipped,
                    heat,
                )
                else -> assertEquals(name, expect.jsonPrimitive.double, (heat as HabitHeat.Share).fraction, 1e-9)
            }
        }
    }

    @Test
    fun `every ring`() {
        vectors.cases("rings").forEach { case ->
            val ring = HabitRules.ring(case.habit(), case.day("today"), case.checkins())
            val expect = case.number("expect")
            if (expect == null) assertNull(case.text("name"), ring) else assertEquals(case.text("name"), expect, ring!!, 1e-9)
        }
    }

    @Test
    fun `every check-in id`() {
        vectors.cases("checkinIds").forEach { case ->
            assertEquals(case.text("habitId"), case.text("expect"), HabitRules.checkinId(case.text("habitId")!!, case.day("day")))
        }
    }

    @Test
    fun `every goal amount`() {
        vectors.cases("goalAmounts").forEach { case ->
            val goalJson = case.getValue("goal").jsonObject
            val goal = GoalItem(
                id = goalJson.text("id")!!,
                title = "Goal",
                horizon = GoalHorizon.of(goalJson.text("horizon"))!!,
                periodStart = goalJson.day("start"),
                mode = GoalRules.MODE_NUMBER,
                unit = goalJson.text("unit"),
            )
            val habits = case.getValue("habits").jsonArray.map { element ->
                val habit = element.jsonObject
                HabitItem(
                    id = habit.text("id")!!,
                    name = "Habit",
                    startsOn = LocalDate.parse("2026-01-01"),
                    measure = habit.text("measure")!!,
                    unit = habit.text("unit"),
                    goalId = habit.text("goalId"),
                )
            }
            val expect = case.getValue("expect").jsonArray.map { it.jsonPrimitive.double }
            assertEquals(case.text("name"), expect, HabitRules.goalAmounts(goal, habits, case.checkins()))
        }
    }
}
