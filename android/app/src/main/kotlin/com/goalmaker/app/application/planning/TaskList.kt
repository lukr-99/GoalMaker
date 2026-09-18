package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tasks as the lists need them: read from the replica, written through its outbox. Every write
 * asks for a sync. The methods block on disk, so callers run them off the main thread.
 */
class TaskList(
    private val replica: Replica,
    private val rows: NewRows,
    private val areas: AreaList,
    private val tags: TagList,
    private val requestSync: () -> Unit,
) {
    /** Open, not deleted, oldest first. */
    fun open(): List<TaskItem> = replica.all(TABLE)
        .filter { it.isNull(SyncedTable.DELETED_AT) && it.text("status") == "open" }
        .map(::toItem)
        .sortedBy(TaskItem::createdAt)

    /** [open], again after every change to the tasks table. Collect it off the main thread. */
    fun watchOpen(): Flow<List<TaskItem>> = replica.watch(TABLE).map { open() }

    /** Every task that isn't deleted, whatever its status: what the list rules work from. */
    fun all(): List<TaskItem> = replica.all(TABLE).filter { it.isNull(SyncedTable.DELETED_AT) }.map(::toItem)

    /** [all], again after every change to the tasks table. Collect it off the main thread. */
    fun watchAll(): Flow<List<TaskItem>> = replica.watch(TABLE).map { all() }

    /** Brings a deleted task back (undo). */
    fun restore(id: String) = change(id) { row -> row[SyncedTable.DELETED_AT] = JsonNull }

    fun add(title: String): TaskItem? = add(ComposerDraft(title = title))

    /**
     * Saves what a composer line says (docs/composer.md): the task, a new area or tags it names, and
     * the tag links, in one transaction. Projects and ideas arrive with M5, so those parts aren't
     * saved yet. Null when the title is blank or nobody is signed in.
     */
    fun add(draft: ComposerDraft): TaskItem? {
        val title = draft.title.trim()
        if (title.isEmpty() || rows.owner() == null) return null
        val item = replica.inTransaction {
            val areaId = draft.area?.let { areas.findOrCreate(it)?.id }
            val tagIds = draft.tags.mapNotNull { tags.findOrCreate(it) }.distinct()
            val task = rows.create(
                TABLE,
                mapOf(
                    "title" to JsonPrimitive(title.take(MAX_TITLE)),
                    "notes" to JsonPrimitive(""),
                    "top_priority" to JsonPrimitive(draft.topPriority),
                    "status" to JsonPrimitive("open"),
                    "position" to JsonPrimitive(0.0),
                    "planned_date" to (draft.plannedDate?.toString()?.let(::JsonPrimitive) ?: JsonNull),
                    "planned_time" to (draft.plannedTime?.format(TIME)?.let(::JsonPrimitive) ?: JsonNull),
                    "area_id" to (areaId?.let(::JsonPrimitive) ?: JsonNull),
                    "recurrence" to (draft.repeat?.let(::JsonPrimitive) ?: JsonNull),
                ),
            ) ?: return@inTransaction null
            replica.queue(TABLE, task)
            val taskId = task.text(SyncedTable.ID)
            tagIds.forEach { tagId ->
                rows.create(TAGS, mapOf("task_id" to JsonPrimitive(taskId), "tag_id" to JsonPrimitive(tagId)))
                    ?.let { replica.queue(TAGS, it) }
            }
            toItem(task)
        }
        if (item != null) requestSync()
        return item
    }

    fun setDone(id: String, done: Boolean) = change(id) { row ->
        row["status"] = JsonPrimitive(if (done) "done" else "open")
        row["completed_at"] = if (done) JsonPrimitive(rows.timestamp()) else JsonNull
    }

    fun delete(id: String) = change(id) { row -> row[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit) {
        val row = replica.get(TABLE, id) ?: return
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
    }

    private fun toItem(row: JsonObject) = TaskItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        title = row.text("title").orEmpty(),
        state = when (row.text("status")) {
            "done" -> TaskState.DONE
            "dropped" -> TaskState.DROPPED
            else -> TaskState.OPEN
        },
        topPriority = (row["top_priority"] as? JsonPrimitive)?.booleanOrNull ?: false,
        createdAt = row.text(SyncedTable.CREATED_AT).orEmpty(),
        plannedDate = row.text("planned_date")?.let(LocalDate::parse),
        plannedTime = row.text("planned_time")?.let(LocalTime::parse),
        areaId = row.text("area_id"),
        recurrence = row.text("recurrence"),
    )

    private companion object {
        const val TABLE = "tasks"
        const val TAGS = "task_tags"
        const val MAX_TITLE = 500
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun JsonObject.isNull(name: String): Boolean = this[name].let { it == null || it == JsonNull }

        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
