package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.sync.SyncRules
import java.time.LocalDate
import java.time.ZoneId

/**
 * The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
 * how a column and the task's own state move together, the order items sit in, which items the
 * who-made-it switch shows, and when a done item leaves the board.
 */
object ProjectRules {
    const val BACKLOG = "backlog"
    const val TODO = "todo"
    const val DOING = "doing"
    const val DONE = "done"

    const val TASK = "task"
    const val IDEA = "idea"
    const val BUG = "bug"

    const val LOW = "low"
    const val NORMAL = "normal"
    const val HIGH = "high"
    const val URGENT = "urgent"

    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val PROJECT_DONE = "done"

    const val OWNER = "owner"
    const val CLAUDE = "claude"
    const val EVERYONE = "all"

    /** The four columns, left to right. */
    val COLUMNS = listOf(BACKLOG, TODO, DOING, DONE)

    /** The priorities, most important first. */
    val PRIORITIES = listOf(URGENT, HIGH, NORMAL, LOW)

    /** Who can make an item (supabase/migrations/0015_task_made_by.sql). */
    val MAKERS = listOf(OWNER, CLAUDE)

    /** What the board's who-made-it switch can show: everything, or one maker's items. */
    val MAKER_FILTERS = listOf(EVERYONE, OWNER, CLAUDE)

    /** Days a done item stays on the board unless its project says otherwise (supabase/migrations/0018_board_archive.sql). */
    const val ARCHIVE_AFTER_DAYS = 14

    /** The days a project can keep done items for; null, outside them, keeps them until archived by hand. */
    val ARCHIVE_DAYS = 1..365

    /** The column a new item of [itemType] lands in: an idea in the backlog, anything else in to do. */
    fun columnFor(itemType: String): String = if (itemType == IDEA) BACKLOG else TODO

    /** How important a priority is; an unknown one counts as normal. */
    fun rank(priority: String): Int = when (priority) {
        URGENT -> 3
        HIGH -> 2
        LOW -> 0
        else -> 1
    }

    /** What moving an item to [column] does to a task in [state]: done finishes it, anything else reopens it. */
    fun moved(column: String, state: TaskState): TaskState = when {
        state == TaskState.DROPPED -> state
        column == DONE -> TaskState.DONE
        state == TaskState.DONE -> TaskState.OPEN
        else -> state
    }

    /** What finishing or reopening a task does to the [column] it sits in. */
    fun finished(state: TaskState, column: String): String = when {
        state == TaskState.DONE -> DONE
        state == TaskState.OPEN && column == DONE -> TODO
        else -> column
    }

    /** One column's items in the order the board shows them. */
    fun order(items: List<TaskItem>): List<TaskItem> = items.sortedWith(
        compareByDescending<TaskItem> { rank(it.priority) }
            .thenBy { it.position }
            .thenBy { it.createdAt }
            .thenBy { it.id },
    )

    /**
     * Whether the switch, set to [filter], shows an item made by [madeBy]. An item that doesn't say is
     * the owner's, and a filter nobody knows shows everything.
     */
    fun shows(filter: String, madeBy: String?): Boolean = when (filter) {
        OWNER, CLAUDE -> (madeBy ?: OWNER) == filter
        else -> true
    }

    /**
     * Whether an item is on its board (contracts/vectors/projects.json 'archive'): anything not done is;
     * a done item is until it is archived by hand or [archiveAfterDays] days after the planning day it
     * was finished, and null days keep it until it is archived by hand.
     */
    fun onBoard(
        state: TaskState,
        completedOn: LocalDate?,
        archiveAfterDays: Int?,
        archivedByHand: Boolean,
        today: LocalDate,
    ): Boolean {
        if (state != TaskState.DONE) return true
        if (archivedByHand) return false
        if (archiveAfterDays == null || completedOn == null) return true
        return today.isBefore(completedOn.plusDays(archiveAfterDays.toLong()))
    }

    /** [onBoard] for an item of [project], finished on the planning day its completion fell on in [zone]. */
    fun onBoard(item: TaskItem, project: ProjectItem, today: LocalDate, zone: ZoneId, startHour: Int): Boolean = onBoard(
        item.state,
        completedOn(item, zone, startHour),
        project.archiveAfterDays,
        item.boardArchivedAt != null,
        today,
    )

    /** The planning day a done item was finished on, by the owner's day start; null while it is not done. */
    fun completedOn(item: TaskItem, zone: ZoneId, startHour: Int): LocalDate? {
        if (item.state != TaskState.DONE) return null
        val instant = item.completedAt?.let(SyncRules::instantOf) ?: return null
        return PlanningDay.of(instant.atZone(zone).toLocalDateTime(), startHour)
    }

    /** The four columns of a project with their items in order; a column with nothing in it stays. */
    fun board(items: List<TaskItem>): List<ProjectColumn> = COLUMNS.map { column ->
        ProjectColumn(column, order(items.filter { !it.deleted && it.boardColumn == column }))
    }
}
