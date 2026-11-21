package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/all-tasks.json, which the Windows app and the connector pass too. */
class AllTasksRulesContractTest {
    private val vectors = ContractFiles.load("vectors/all-tasks.json")

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.optional(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun state(name: String) = when (name) {
        "done" -> TaskState.DONE
        "dropped" -> TaskState.DROPPED
        else -> TaskState.OPEN
    }

    private fun tasks(case: JsonObject) = case.getValue("tasks").jsonArray.map { element ->
        val task = element.jsonObject
        TaskItem(
            id = task.text("id"),
            title = task.optional("title") ?: task.text("id"),
            state = state(task.text("state")),
            topPriority = false,
            createdAt = task.text("createdAt"),
            deleted = task["deleted"]?.jsonPrimitive?.boolean ?: false,
            notes = task.optional("notes") ?: "",
            plannedDate = task.optional("day")?.let(LocalDate::parse),
            deadline = task.optional("deadline")?.let(LocalDate::parse),
            areaId = task.optional("area"),
            projectId = task.optional("project"),
        )
    }

    @Test
    fun `the groups`() {
        vectors.getValue("groups").jsonArray.map { it.jsonObject }.forEach { case ->
            val groups = AllTasksRules.groups(
                tasks(case),
                case.getValue("areas").jsonArray.map { it.jsonPrimitive.content },
                case.getValue("projects").jsonObject.mapValues { (it.value as? JsonPrimitive)?.takeIf { value -> value.isString }?.content },
                case.text("query"),
                case.getValue("projectItems").jsonPrimitive.boolean,
            )
            val expect = case.getValue("expect").jsonArray.map { element ->
                val group = element.jsonObject
                group.optional("area") to group.getValue("tasks").jsonArray.map { it.jsonPrimitive.content }
            }
            assertEquals(case.text("name"), expect, groups.map { it.areaId to it.tasks.map(TaskItem::id) })
        }
    }
}
