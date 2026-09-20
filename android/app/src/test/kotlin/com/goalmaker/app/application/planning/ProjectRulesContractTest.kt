package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/projects.json, which the Windows app and the connector pass too. */
class ProjectRulesContractTest {
    private val vectors = ContractFiles.load("vectors/projects.json")

    private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    private fun state(name: String?) = when (name) {
        "done" -> TaskState.DONE
        "dropped" -> TaskState.DROPPED
        else -> TaskState.OPEN
    }

    private fun items(case: JsonObject) = case.getValue("items").jsonArray.map { element ->
        val item = element.jsonObject
        TaskItem(
            id = item.text("id")!!,
            title = item.text("id")!!,
            state = state(item.text("state")),
            topPriority = false,
            createdAt = item.text("createdAt")!!,
            boardColumn = item.text("column"),
            projectId = "p",
            priority = item.text("priority")!!,
            position = item.getValue("position").jsonPrimitive.double,
        )
    }

    @Test
    fun `the columns and priorities are the ones the contract names`() {
        assertEquals(vectors.getValue("columns").jsonArray.map { it.jsonPrimitive.content }, ProjectRules.COLUMNS)
        assertEquals(vectors.getValue("priorities").jsonArray.map { it.jsonPrimitive.content }, ProjectRules.PRIORITIES)
    }

    @Test
    fun `every new item lands in its column`() {
        vectors.cases("newItems").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), ProjectRules.columnFor(case.text("type")!!))
        }
    }

    @Test
    fun `every move`() {
        vectors.cases("moves").forEach { case ->
            val expect = case.getValue("expect").jsonObject
            val column = case.text("column")!!
            assertEquals(case.text("name"), expect.text("column"), column)
            assertEquals(case.text("name"), state(expect.text("state")), ProjectRules.moved(column, state(case.text("state"))))
        }
    }

    @Test
    fun `every finish`() {
        vectors.cases("finishing").forEach { case ->
            assertEquals(
                case.text("name"),
                case.text("expect"),
                ProjectRules.finished(state(case.text("state")), case.text("column")!!),
            )
        }
    }

    @Test
    fun `every order`() {
        vectors.cases("order").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonArray.map { it.jsonPrimitive.content },
                ProjectRules.order(items(case)).map(TaskItem::id),
            )
        }
    }

    @Test
    fun `every board`() {
        vectors.cases("board").forEach { case ->
            val expect = case.getValue("expect").jsonObject
            val board = ProjectRules.board(items(case))
            assertEquals(case.text("name"), ProjectRules.COLUMNS, board.map(ProjectColumn::column))
            board.forEach { column ->
                assertEquals(
                    "${case.text("name")} ${column.column}",
                    expect.getValue(column.column).jsonArray.map { it.jsonPrimitive.content },
                    column.items.map(TaskItem::id),
                )
            }
        }
    }
}
