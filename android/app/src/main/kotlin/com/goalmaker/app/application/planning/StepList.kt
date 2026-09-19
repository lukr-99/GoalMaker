package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * A task's checklist: read from the replica, written through its outbox. The methods block on disk,
 * so callers run them off the main thread.
 */
class StepList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** The steps of [taskId] in their order. */
    fun forTask(taskId: String): List<StepItem> = live(taskId).map(::toItem)

    /** [forTask], again after every change to the steps. Collect it off the main thread. */
    fun watch(taskId: String): Flow<List<StepItem>> = replica.watch(TABLE).map { forTask(taskId) }

    /** Adds a step at the end. Null when the title is blank or nobody is signed in. */
    fun add(taskId: String, title: String): StepItem? {
        val trimmed = title.trim().take(MAX_TITLE)
        if (trimmed.isEmpty()) return null
        val position = (live(taskId).maxOfOrNull { it.position() } ?: -1.0) + 1.0
        val row = rows.create(
            TABLE,
            mapOf(
                "task_id" to JsonPrimitive(taskId),
                "title" to JsonPrimitive(trimmed),
                "done" to JsonPrimitive(false),
                "position" to JsonPrimitive(position),
            ),
        ) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** A new title. False when it is blank. */
    fun rename(id: String, title: String): Boolean {
        val trimmed = title.trim().take(MAX_TITLE)
        if (trimmed.isEmpty()) return false
        return change(id) { it["title"] = JsonPrimitive(trimmed) }
    }

    fun setDone(id: String, done: Boolean) = change(id) { it["done"] = JsonPrimitive(done) }

    fun delete(id: String) = change(id) { it[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Moves a step to [index] in its checklist and numbers the checklist again. */
    fun move(id: String, index: Int) {
        val step = replica.get(TABLE, id) ?: return
        val order = live(step.text("task_id")).toMutableList()
        val at = order.indexOfFirst { it.text(SyncedTable.ID) == id }
        if (at < 0) return
        val moved = order.removeAt(at)
        order.add(index.coerceIn(0, order.size), moved)
        replica.inTransaction {
            order.forEachIndexed { position, row ->
                replica.queue(TABLE, JsonObject(row + ("position" to JsonPrimitive(position.toDouble()))))
            }
        }
        requestSync()
    }

    private fun live(taskId: String): List<JsonObject> = replica.all(TABLE)
        .filter { it.text("task_id") == taskId && it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
        .sortedWith(compareBy({ it.position() }, { it.text(SyncedTable.CREATED_AT) }, { it.text(SyncedTable.ID) }))

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = StepItem(
        id = row.text(SyncedTable.ID),
        taskId = row.text("task_id"),
        title = row.text("title"),
        done = (row["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
    )

    private companion object {
        const val TABLE = "task_steps"
        const val MAX_TITLE = 300

        fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content.orEmpty()

        fun JsonObject.position() = (this["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    }
}
