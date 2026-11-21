package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/focus.json, which the Windows app and the connector pass too. */
class FocusBoardRulesContractTest {
    private val vectors = ContractFiles.load("vectors/focus.json")

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    private fun state(name: String) = when (name) {
        "done" -> TaskState.DONE
        "dropped" -> TaskState.DROPPED
        else -> TaskState.OPEN
    }

    private fun tasks(case: JsonObject) = case.getValue("tasks").jsonArray.map { element ->
        val task = element.jsonObject
        TaskItem(
            id = task.text("id"),
            title = task.text("id"),
            state = state(task.text("state")),
            topPriority = false,
            createdAt = task.text("createdAt"),
            deleted = task["deleted"]?.jsonPrimitive?.boolean ?: false,
            focusPosition = task.getValue("focus").jsonPrimitive.doubleOrNull,
        )
    }

    @Test
    fun `the board's order`() {
        vectors.cases("board").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonArray.map { it.jsonPrimitive.content },
                FocusBoardRules.board(tasks(case)).map(TaskItem::id),
            )
        }
    }

    @Test
    fun `adding at the end`() {
        vectors.cases("append").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.double,
                FocusBoardRules.appendPosition(tasks(case)),
                0.0,
            )
        }
    }

    @Test
    fun `putting a task at a place`() {
        vectors.cases("placeAt").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.double,
                FocusBoardRules.placeAt(tasks(case), case.text("task"), case.getValue("place").jsonPrimitive.int),
                0.0,
            )
        }
    }
}
