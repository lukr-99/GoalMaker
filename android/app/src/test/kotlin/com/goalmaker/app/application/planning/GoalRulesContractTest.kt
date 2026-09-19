package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/goals.json, which the Windows app and the connector pass too. */
class GoalRulesContractTest {
    private val vectors = ContractFiles.load("vectors/goals.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = LocalDate.parse(text(name)!!)
    private fun JsonObject.horizon(name: String = "horizon") = GoalHorizon.of(text(name))!!
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `every period`() {
        vectors.cases("periods").forEach { case ->
            val start = GoalRules.periodStart(case.horizon(), case.day("day"))
            assertEquals("${case.text("horizon")} ${case.text("day")}", case.day("start"), start)
            assertEquals("${case.text("horizon")} ${case.text("day")} end", case.day("end"), GoalRules.periodEnd(case.horizon(), start))
        }
    }

    @Test
    fun `every parent`() {
        vectors.cases("parents").forEach { case ->
            val child = case.getValue("child").jsonObject
            val parent = case.getValue("parent").jsonObject
            assertEquals(
                case.text("name"),
                case.getValue("allowed").jsonPrimitive.boolean,
                GoalRules.canServe(child.horizon(), child.day("start"), parent.horizon(), parent.day("start")),
            )
        }
    }

    @Test
    fun `every progress`() {
        vectors.cases("progress").forEach { case ->
            val goal = case.getValue("goal").jsonObject
            val tasks = case.getValue("tasks").jsonArray.mapIndexed { index, element ->
                val task = element.jsonObject
                TaskItem(
                    id = "t$index",
                    title = "Task",
                    state = when (task.text("status")) {
                        "done" -> TaskState.DONE
                        "dropped" -> TaskState.DROPPED
                        else -> TaskState.OPEN
                    },
                    topPriority = false,
                    createdAt = "2026-09-10T08:00:00.000000Z",
                    deleted = task["deleted"]?.jsonPrimitive?.boolean ?: false,
                )
            }
            val entries = case.getValue("entries").jsonArray.mapIndexed { index, element ->
                val entry = element.jsonObject
                GoalEntryItem(
                    "e$index",
                    "g",
                    LocalDate.parse("2026-09-18"),
                    entry.getValue("amount").jsonPrimitive.double,
                    entry["deleted"]?.jsonPrimitive?.boolean ?: false,
                )
            }
            val expect = case.getValue("expect").jsonObject
            val progress = GoalRules.progress(goal.text("mode")!!, goal.text("status")!!, goal["target"]?.jsonPrimitive?.double, tasks, entries)
            val name = case.text("name")
            assertEquals(name, expect.getValue("value").jsonPrimitive.double, progress.value, 1e-9)
            assertEquals(name, expect.getValue("target").jsonPrimitive.double, progress.target, 1e-9)
            assertEquals(name, expect.getValue("fraction").jsonPrimitive.double, progress.fraction, 1e-9)
            assertEquals(name, expect.getValue("hit").jsonPrimitive.boolean, progress.hit)
        }
    }

    @Test
    fun `every copy`() {
        vectors.cases("copy").forEach { case ->
            val to = case.getValue("to").jsonObject
            val goals = case.cases("goals").map { goal ->
                GoalItem(
                    id = goal.text("id")!!,
                    title = goal.text("title")!!,
                    horizon = to.horizon(),
                    periodStart = to.day("start").minusWeeks(1),
                    status = goal.text("status")!!,
                    parentId = goal.text("parent"),
                )
            }
            val parents = case.cases("parents").associate { parent ->
                parent.text("id")!! to GoalItem(parent.text("id")!!, "Parent", parent.horizon(), parent.day("start"))
            }
            val expected = case.cases("expect").map { it.text("title") to it.text("parent") }
            val actual = GoalRules.copies(goals, parents, to.horizon(), to.day("start")).map { it.title to it.parentId }
            assertEquals(case.text("name"), expected, actual)
        }
    }
}
