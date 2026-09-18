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

/** Runs contracts/vectors/lists.json, which the Windows app passes too. */
class ListRulesContractTest {
    private val vectors = ContractFiles.load("vectors/lists.json")

    @Test
    fun `every filter`() {
        vectors.getValue("filter").jsonArray.map { it.jsonObject }.forEach { case ->
            val listed = case.getValue("tasks").jsonArray.map { it.jsonObject }
            val tasks = listed.map { task ->
                TaskItem(
                    id = task.getValue("id").jsonPrimitive.content,
                    title = "Task",
                    state = TaskState.OPEN,
                    topPriority = false,
                    createdAt = "2026-09-10T08:00:00.000000Z",
                    areaId = task["area"]?.jsonPrimitive?.content,
                )
            }
            val links = listed.associate { task ->
                task.getValue("id").jsonPrimitive.content to task["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
            }
            val filter = ListFilter(
                areaId = case.getValue("area").let { if (it == JsonNull) null else it.jsonPrimitive.content },
                tagId = case.getValue("tag").let { if (it == JsonNull) null else it.jsonPrimitive.content },
            )
            val expected = case.getValue("keep").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(case.getValue("name").jsonPrimitive.content, expected, filter.apply(tasks, links).map(TaskItem::id))
        }
    }

    @Test
    fun `every list vector`() {
        val defaults = vectors.getValue("taskDefaults").jsonObject
        val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val now = LocalDateTime.parse((case["now"] ?: vectors.getValue("now")).jsonPrimitive.content)
            val startHour = (case["rolloverHour"] ?: vectors.getValue("rolloverHour")).jsonPrimitive.int
            val tasks = case.getValue("tasks").jsonArray.mapIndexed { index, element ->
                task(JsonObject(defaults + element.jsonObject), index)
            }
            val lists = ListRules.lists(tasks, PlanningDay.of(now, startHour))
            val expect = case.getValue("expect").jsonObject
            fun ids(name: String) = expect[name]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val summary = expect["summary"]?.jsonObject
            val expected = listOf(
                ids("priorities"), ids("scheduled"), ids("more"), ids("overdue"), ids("tomorrow"), ids("inbox"),
                listOf("${summary?.get("done")?.jsonPrimitive?.int ?: 0} of ${summary?.get("total")?.jsonPrimitive?.int ?: 0}"),
            )
            val sections = lists.todaySections
            val actual = listOf(
                sections.priorities, sections.scheduled, sections.more, sections.overdue, lists.tomorrow, lists.inbox,
            ).map { list -> list.map(TaskItem::id) } + listOf(listOf("${lists.summary.done} of ${lists.summary.total}"))
            if (actual == expected) null else "${case.getValue("name").jsonPrimitive.content}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
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
