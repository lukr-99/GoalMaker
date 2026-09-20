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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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
    private val projects: ProjectList,
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
     * Saves what a composer line says (docs/composer.md): the task, a new area, project or tags it
     * names, and the tag links, in one transaction. A `?` item is an idea, which lands in the
     * backlog when it has a project. Null when the title is blank or nobody is signed in.
     */
    fun add(draft: ComposerDraft, notes: String = ""): TaskItem? {
        val title = draft.title.trim()
        if (title.isEmpty() || rows.owner() == null) return null
        val item = replica.inTransaction {
            val areaId = draft.area?.let { areas.findOrCreate(it)?.id }
            val projectId = draft.project?.let { projects.findOrCreate(it)?.id }
            val itemType = if (draft.idea) ProjectRules.IDEA else ProjectRules.TASK
            val tagIds = draft.tags.mapNotNull { tags.findOrCreate(it) }.distinct()
            val task = rows.create(
                TABLE,
                mapOf(
                    "title" to JsonPrimitive(title.take(MAX_TITLE)),
                    "notes" to JsonPrimitive(notes.take(MAX_NOTES)),
                    "top_priority" to JsonPrimitive(draft.topPriority),
                    "status" to JsonPrimitive("open"),
                    "position" to JsonPrimitive(0.0),
                    "moved_count" to JsonPrimitive(0),
                    "planned_date" to (draft.plannedDate?.toString()?.let(::JsonPrimitive) ?: JsonNull),
                    "planned_time" to (draft.plannedTime?.format(TIME)?.let(::JsonPrimitive) ?: JsonNull),
                    "area_id" to (areaId?.let(::JsonPrimitive) ?: JsonNull),
                    "recurrence" to (draft.repeat?.let(::JsonPrimitive) ?: JsonNull),
                    "project_id" to (projectId?.let(::JsonPrimitive) ?: JsonNull),
                    "item_type" to JsonPrimitive(itemType),
                    "board_column" to (
                        projectId?.let { JsonPrimitive(ProjectRules.columnFor(itemType)) } ?: JsonNull
                        ),
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
    fun plan(id: String, day: LocalDate) = reopen(id) { row ->
        val current = toItem(JsonObject(row))
        row["moved_count"] = JsonPrimitive(PlanRules.moves(current.plannedDate, day, current.movedCount))
        row["planned_date"] = JsonPrimitive(day.toString())
    }

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

    /** The task with this id, whatever its status, or null when it is gone. */
    fun find(id: String): TaskItem? = all().firstOrNull { it.id == id }

    /** [find], again after every change to the tasks table. Collect it off the main thread. */
    fun watch(id: String): Flow<TaskItem?> = replica.watch(TABLE).map { find(id) }

    /** A new title. False when it is blank, which the task can't have. */
    fun rename(id: String, title: String): Boolean {
        val trimmed = title.trim().take(MAX_TITLE)
        if (trimmed.isEmpty()) return false
        change(id) { row -> row["title"] = JsonPrimitive(trimmed) }
        return true
    }

    /** The notes, in light Markdown (docs/archive.md), up to 20,000 characters. */
    fun setNotes(id: String, notes: String) = change(id) { row -> row["notes"] = JsonPrimitive(notes.take(MAX_NOTES)) }

    /**
     * The planned day and time, without reopening the task (Plan tomorrow's [plan] does that). A time
     * needs a day, so no day clears the time too.
     */
    fun schedule(id: String, day: LocalDate?, time: LocalTime?) = change(id) { row ->
        val current = toItem(JsonObject(row))
        row["moved_count"] = JsonPrimitive(PlanRules.moves(current.plannedDate, day, current.movedCount))
        row["planned_date"] = day?.toString()?.let(::JsonPrimitive) ?: JsonNull
        row["planned_time"] = if (day == null) JsonNull else time?.format(TIME)?.let(::JsonPrimitive) ?: JsonNull
    }

    fun setDeadline(id: String, day: LocalDate?) = change(id) { row -> row["deadline"] = day?.toString()?.let(::JsonPrimitive) ?: JsonNull }

    fun setArea(id: String, areaId: String?) = change(id) { row -> row["area_id"] = areaId?.let(::JsonPrimitive) ?: JsonNull }

    /** Makes the task serve [goalId] (docs/goals.md), or no goal when it is null. */
    fun setGoal(id: String, goalId: String?) = change(id) { row -> row[GOAL_ID] = goalId?.let(::JsonPrimitive) ?: JsonNull }

    /**
     * How the task repeats (docs/repeating.md), or not at all when [rule] is null. False for a rule the
     * apps can't follow. A task that starts repeating becomes the first of its series.
     */
    fun setRecurrence(id: String, rule: String?): Boolean {
        if (rule != null && Recurrence.parse(rule) == null) return false
        change(id) { row ->
            row["recurrence"] = rule?.let(::JsonPrimitive) ?: JsonNull
            if (rule != null && row[SERIES_ID].let { it == null || it == JsonNull }) row[SERIES_ID] = JsonPrimitive(id)
        }
        return true
    }

    /** Links the task to exactly these tags, by name, creating tags it names for the first time. */
    fun setTags(id: String, names: List<String>) {
        replica.inTransaction {
            val wanted = names.mapNotNull { tags.findOrCreate(it) }.toSet()
            val links = replica.all(TAGS).filter { it.text("task_id") == id && it.isNull(SyncedTable.DELETED_AT) }
            links.filter { it.text("tag_id") !in wanted }.forEach { link ->
                replica.queue(TAGS, JsonObject(link + (SyncedTable.DELETED_AT to JsonPrimitive(rows.timestamp()))))
            }
            val linked = links.mapNotNull { it.text("tag_id") }.toSet()
            (wanted - linked).forEach { tagId ->
                rows.create(TAGS, mapOf("task_id" to JsonPrimitive(id), "tag_id" to JsonPrimitive(tagId)))?.let { replica.queue(TAGS, it) }
            }
        }
        requestSync()
    }

    // Done or dropped; an open repeating task makes its next occurrence in the same transaction.
    private fun finish(id: String, status: String) {
        val changed = replica.inTransaction {
            val row = replica.get(TABLE, id) ?: return@inTransaction false
            val wasOpen = row.text("status") == "open"
            val values = LinkedHashMap(row)
            values["status"] = JsonPrimitive(status)
            values["completed_at"] = if (status == "done") JsonPrimitive(rows.timestamp()) else JsonNull
            boardColumn(values)?.let { column ->
                values["board_column"] = JsonPrimitive(ProjectRules.finished(stateOf(status), column))
            }
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
            boardColumn(values)?.let { column ->
                values["board_column"] = JsonPrimitive(ProjectRules.finished(TaskState.OPEN, column))
            }
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
                "moved_count" to JsonPrimitive(0),
                "planned_date" to JsonPrimitive(day.toString()),
                "planned_time" to (row["planned_time"] ?: JsonNull),
                "area_id" to (current.areaId?.let(::JsonPrimitive) ?: JsonNull),
                "recurrence" to (current.recurrence?.let(::JsonPrimitive) ?: JsonNull),
                SERIES_ID to JsonPrimitive(Occurrences.seriesOf(current)),
                GOAL_ID to (goalFor(current.goalId, day)?.let(::JsonPrimitive) ?: JsonNull),
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

    // The goal a next occurrence on [day] still serves: the current one's, while its period lasts (docs/repeating.md).
    private fun goalFor(goalId: String?, day: LocalDate): String? {
        val goal = goalId?.let { replica.get(GOALS, it) }?.takeIf { it.isNull(SyncedTable.DELETED_AT) } ?: return null
        val horizon = GoalHorizon.of(goal.text("horizon")) ?: return null
        val start = goal.text("period_start")?.let(LocalDate::parse) ?: return null
        return goalId.takeIf { !day.isBefore(start) && !day.isAfter(GoalRules.periodEnd(horizon, start)) }
    }

    /**
     * Puts a task in a project, or takes it out of one (docs/projects.md). A new item lands in the
     * column its type calls for; taking it out leaves a plain task with no column and no milestone.
     */
    fun setProject(id: String, projectId: String?, itemType: String = ProjectRules.TASK) = change(id) { row ->
        row["project_id"] = projectId?.let(::JsonPrimitive) ?: JsonNull
        if (projectId == null) {
            row["board_column"] = JsonNull
            row["milestone_id"] = JsonNull
        } else {
            row["item_type"] = JsonPrimitive(itemType)
            if (boardColumn(row) == null) row["board_column"] = JsonPrimitive(ProjectRules.columnFor(itemType))
        }
    }

    /** Moves an item to a board column; the done column finishes the task and any other reopens it. */
    fun setBoardColumn(id: String, column: String) {
        if (column !in ProjectRules.COLUMNS) return
        val current = find(id) ?: return
        if (current.projectId == null) return
        when (ProjectRules.moved(column, current.state)) {
            TaskState.DONE -> if (current.state != TaskState.DONE) setDone(id, true)
            TaskState.OPEN -> if (current.state == TaskState.DONE) setDone(id, false)
            else -> Unit
        }
        change(id) { row -> row["board_column"] = JsonPrimitive(column) }
    }

    /** What kind of item this is: a task, an idea or a bug. */
    fun setItemType(id: String, itemType: String) {
        if (itemType !in setOf(ProjectRules.TASK, ProjectRules.IDEA, ProjectRules.BUG)) return
        change(id) { row -> row["item_type"] = JsonPrimitive(itemType) }
    }

    /** How important the task is: low, normal, high or urgent. */
    fun setPriority(id: String, priority: String) {
        if (priority !in ProjectRules.PRIORITIES) return
        change(id) { row -> row["priority"] = JsonPrimitive(priority) }
    }

    /** The milestone of the item's own project, or none. */
    fun setMilestone(id: String, milestoneId: String?) = change(id) { row ->
        row["milestone_id"] = milestoneId?.let(::JsonPrimitive) ?: JsonNull
    }

    private fun stateOf(status: String) = when (status) {
        "done" -> TaskState.DONE
        "dropped" -> TaskState.DROPPED
        else -> TaskState.OPEN
    }

    // The column a row sits in, or null when it isn't a project item.
    private fun boardColumn(values: Map<String, JsonElement>) =
        (values["board_column"] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content

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
        movedCount = (row["moved_count"] as? JsonPrimitive)?.intOrNull ?: 0,
        plannedTime = row.text("planned_time")?.let(LocalTime::parse),
        areaId = row.text("area_id"),
        recurrence = row.text("recurrence"),
        seriesId = row.text(SERIES_ID),
        notes = row.text("notes").orEmpty(),
        deadline = row.text("deadline")?.let(LocalDate::parse),
        completedAt = row.text("completed_at"),
        goalId = row.text(GOAL_ID),
        projectId = row.text("project_id"),
        itemType = row.text("item_type") ?: ProjectRules.TASK,
        boardColumn = row.text("board_column"),
        priority = row.text("priority") ?: ProjectRules.NORMAL,
        milestoneId = row.text("milestone_id"),
        position = (row["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
    )

    private companion object {
        const val TABLE = "tasks"
        const val TAGS = "task_tags"
        const val SERIES_ID = "series_id"
        const val GOAL_ID = "goal_id"
        const val GOALS = "goals"
        const val MAX_TITLE = 500
        const val MAX_NOTES = 20_000
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun JsonObject.isNull(name: String): Boolean = this[name].let { it == null || it == JsonNull }

        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
