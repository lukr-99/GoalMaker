package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.planning.Recurrence
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
 * asks for a sync. Finishing a repeating task makes its next occurrence (docs/repeating.md);
 * [today] is the planning day that counts from. The methods block on disk, so callers run them off
 * the main thread.
 */
class TaskList(
    private val replica: Replica,
    private val rows: NewRows,
    private val areas: AreaList,
    private val tags: TagList,
    private val requestSync: () -> Unit,
    private val today: () -> LocalDate,
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
            val placed = if (draft.repeat != null) JsonObject(task + (SERIES_ID to task.getValue(SyncedTable.ID))) else task
            replica.queue(TABLE, placed)
            val taskId = task.text(SyncedTable.ID)
            tagIds.forEach { tagId ->
                rows.create(TAGS, mapOf("task_id" to JsonPrimitive(taskId), "tag_id" to JsonPrimitive(tagId)))
                    ?.let { replica.queue(TAGS, it) }
            }
            toItem(placed)
        }
        if (item != null) requestSync()
        return item
    }

    fun setDone(id: String, done: Boolean) = if (done) finish(id, "done") else reopen(id) {}

    fun delete(id: String) = change(id) { row -> row[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Plans the task for [day], keeping its time; reopens it if it was done or dropped (Plan tomorrow). */
    fun plan(id: String, day: LocalDate) = reopen(id) { row -> row["planned_date"] = JsonPrimitive(day.toString()) }

    /** Drops the task: it stays in the history but leaves every list. A repeating task moves on. */
    fun drop(id: String) = finish(id, "dropped")

    /**
     * After a sync that pulled rows: drops all but one open occurrence of each series, the same way on
     * every device (docs/repeating.md). True when it changed something.
     */
    fun repairSeries(): Boolean {
        val drop = Occurrences.toDrop(all())
        if (drop.isEmpty()) return false
        replica.inTransaction {
            drop.forEach { id ->
                replica.get(TABLE, id)?.let { row ->
                    replica.queue(TABLE, JsonObject(row + mapOf("status" to JsonPrimitive("dropped"), "completed_at" to JsonNull)))
                }
            }
        }
        requestSync()
        return true
    }

    fun setTopPriority(id: String, top: Boolean) = change(id) { row -> row["top_priority"] = JsonPrimitive(top) }

    // Done or dropped; an open repeating task makes its next occurrence in the same transaction.
    private fun finish(id: String, status: String) {
        val changed = replica.inTransaction {
            val row = replica.get(TABLE, id) ?: return@inTransaction false
            val wasOpen = row.text("status") == "open"
            val values = LinkedHashMap(row)
            values["status"] = JsonPrimitive(status)
            values["completed_at"] = if (status == "done") JsonPrimitive(rows.timestamp()) else JsonNull
            val finished = JsonObject(values)
            replica.queue(TABLE, finished)
            if (wasOpen) moveOn(finished)
            true
        }
        if (changed) requestSync()
    }

    // Open again; a finished occurrence takes back its next one if that is still open (docs/repeating.md).
    private fun reopen(id: String, edit: (MutableMap<String, JsonElement>) -> Unit) {
        val changed = replica.inTransaction {
            val row = replica.get(TABLE, id) ?: return@inTransaction false
            val wasFinished = row.text("status") != "open"
            val values = LinkedHashMap(row)
            values["status"] = JsonPrimitive("open")
            values["completed_at"] = JsonNull
            edit(values)
            replica.queue(TABLE, JsonObject(values))
            val next = if (wasFinished) replica.get(TABLE, Occurrences.successorId(id)) else null
            if (next != null && next.isNull(SyncedTable.DELETED_AT) && next.text("status") == "open") {
                replica.queue(TABLE, JsonObject(next + (SyncedTable.DELETED_AT to JsonPrimitive(rows.timestamp()))))
            }
            true
        }
        if (changed) requestSync()
    }

    // The next occurrence copies this one's plan onto the rule's next day, with its tags; nothing when
    // the task doesn't repeat, the rule can't be followed, or the next occurrence is already there.
    private fun moveOn(row: JsonObject) {
        val current = toItem(row)
        val day = Recurrence.parse(current.recurrence)?.next(current.plannedDate, today()) ?: return
        val nextId = Occurrences.successorId(current.id)
        if (replica.get(TABLE, nextId)?.isNull(SyncedTable.DELETED_AT) == true) return
        val next = rows.create(
            TABLE,
            mapOf(
                SyncedTable.ID to JsonPrimitive(nextId),
                "title" to (row["title"] ?: JsonNull),
                "notes" to (row["notes"] ?: JsonPrimitive("")),
                "top_priority" to JsonPrimitive(current.topPriority),
                "status" to JsonPrimitive("open"),
                "position" to JsonPrimitive(0.0),
                "planned_date" to JsonPrimitive(day.toString()),
                "planned_time" to (row["planned_time"] ?: JsonNull),
                "area_id" to (current.areaId?.let(::JsonPrimitive) ?: JsonNull),
                "recurrence" to (current.recurrence?.let(::JsonPrimitive) ?: JsonNull),
                SERIES_ID to JsonPrimitive(Occurrences.seriesOf(current)),
            ),
        ) ?: return
        replica.queue(TABLE, next)
        replica.all(TAGS)
            .filter { it.text("task_id") == current.id && it.isNull(SyncedTable.DELETED_AT) }
            .mapNotNull { it.text("tag_id") }
            .forEach { tagId ->
                rows.create(
                    TAGS,
                    mapOf(
                        SyncedTable.ID to JsonPrimitive(Occurrences.tagLinkId(nextId, tagId)),
                        "task_id" to JsonPrimitive(nextId),
                        "tag_id" to JsonPrimitive(tagId),
                    ),
                )?.let { replica.queue(TAGS, it) }
            }
    }

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
        seriesId = row.text(SERIES_ID),
    )

    private companion object {
        const val TABLE = "tasks"
        const val TAGS = "task_tags"
        const val SERIES_ID = "series_id"
        const val MAX_TITLE = 500
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun JsonObject.isNull(name: String): Boolean = this[name].let { it == null || it == JsonNull }

        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
