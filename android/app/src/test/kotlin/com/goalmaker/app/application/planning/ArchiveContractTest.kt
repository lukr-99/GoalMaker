package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/archive.json, which the Windows app passes too. */
class ArchiveContractTest {
    private val vectors = ContractFiles.load("vectors/archive.json")

    @Test
    fun `every archive search`() {
        vectors.getValue("cases").jsonArray.map { it.jsonObject }.forEach { case ->
            val tasks = case.getValue("tasks").jsonArray.map { it.jsonObject }.map { task ->
                TaskItem(
                    id = task.getValue("id").jsonPrimitive.content,
                    title = task.getValue("title").jsonPrimitive.content,
                    state = when (task.getValue("status").jsonPrimitive.content) {
                        "done" -> TaskState.DONE
                        "dropped" -> TaskState.DROPPED
                        else -> TaskState.OPEN
                    },
                    topPriority = false,
                    createdAt = "2026-09-10T08:00:00.000000Z",
                    deleted = task["deleted"]?.jsonPrimitive?.boolean ?: false,
                    notes = task["notes"]?.jsonPrimitive?.content.orEmpty(),
                    completedAt = task["completedAt"]?.jsonPrimitive?.content,
                )
            }
            val expected = case.getValue("keep").jsonArray.map { it.jsonPrimitive.content }
            val actual = ArchiveRules.search(tasks, case.getValue("query").jsonPrimitive.content).map(TaskItem::id)
            assertEquals(case.getValue("name").jsonPrimitive.content, expected, actual)
        }
    }
}
