package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/plan.json, which the Windows app passes too. */
class PlanRulesContractTest {
    private val vectors = ContractFiles.load("vectors/plan.json")

    @Test
    fun `the priority limit matches the contract`() {
        assertEquals(vectors.getValue("maxPriorities").jsonPrimitive.int, PlanRules.MAX_PRIORITIES)
    }

    @Test
    fun `every plan vector`() {
        val defaults = vectors.getValue("taskDefaults").jsonObject
        val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val now = LocalDateTime.parse((case["now"] ?: vectors.getValue("now")).jsonPrimitive.content)
            val startHour = (case["rolloverHour"] ?: vectors.getValue("rolloverHour")).jsonPrimitive.int
            val today = PlanningDay.of(now, startHour)
            val tasks = case.getValue("tasks").jsonArray.mapIndexed { index, element ->
                task(JsonObject(defaults + element.jsonObject), index)
            }
            val expect = case.getValue("expect").jsonObject
            val expected = mutableListOf(
                "review=" + expect["review"]?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty(),
                "priorities=" + (expect["priorities"]?.jsonPrimitive?.int ?: 0),
                "tomorrow=" + expect["tomorrow"]?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty(),
            )
            val actual = mutableListOf(
                "review=" + PlanRules.review(tasks, today).joinToString(",") { it.id },
                "priorities=" + PlanRules.priorities(tasks, today),
                "tomorrow=" + PlanRules.tomorrow(tasks, today).joinToString(",") { it.id },
            )
            expect["decisions"]?.jsonObject?.let { decisions ->
                expected += "decisions=" + decisions.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.jsonPrimitive.content}" }
                actual += "decisions=" + tasks.filter { !it.deleted }.sortedBy { it.id }
                    .joinToString(",") { "${it.id}:${PlanRules.decision(it, today).name.lowercase()}" }
            }
            if (actual == expected) null else "${case.getValue("name").jsonPrimitive.content}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `every move count`() {
        vectors.getValue("moves").jsonArray.map { it.jsonObject }.forEach { case ->
            fun day(name: String) = case[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let(LocalDate::parse)
            assertEquals(
                case.getValue("name").jsonPrimitive.content,
                case.getValue("expect").jsonPrimitive.int,
                PlanRules.moves(day("before"), day("after"), case.getValue("count").jsonPrimitive.int),
            )
        }
    }

    private fun task(fields: JsonObject, index: Int): TaskItem {
        fun text(name: String) = fields[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
        return TaskItem(
            id = text("id")!!,
            title = text("id")!!,
            state = when (text("status")) {
                "done" -> TaskState.DONE
                "dropped" -> TaskState.DROPPED
                else -> TaskState.OPEN
            },
            topPriority = fields.getValue("top").jsonPrimitive.boolean,
            createdAt = "2026-09-10T08:%02d:00.000000Z".format(index),
            plannedDate = text("planned")?.let(LocalDate::parse),
            plannedTime = text("time")?.let(LocalTime::parse),
            areaId = text("area"),
            deleted = fields.getValue("deleted").jsonPrimitive.boolean,
        )
    }
}
