package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.sync.SyncRules
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
 * how a column and the task's own state move together, the order items sit in, which items the
 * who-made-it switch shows, when a done item leaves the board, and the short ids its items read by
 * (GM-12).
 */
object ProjectRules {
    const val BACKLOG = "backlog"
    const val TODO = "todo"
    const val DOING = "doing"
    const val DONE = "done"

    /** The board's last column: every dropped item, whatever column it is stored in (docs/projects.md). */
    const val DROPPED = "dropped"

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

    /** The four columns an item is stored in, left to right. */
    val COLUMNS = listOf(BACKLOG, TODO, DOING, DONE)

    /** The columns a board shows: the four, then [DROPPED]. */
    val BOARD_COLUMNS = COLUMNS + DROPPED

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

    /** The shortest and the longest a project's key can be (docs/projects.md, "Item ids"). */
    const val SHORTEST_ITEM_KEY = 2
    const val LONGEST_ITEM_KEY = 6

    // A suggested key has at most this many characters before a number is added for a taken one.
    private const val LONGEST_SUGGESTED_KEY = 4
    private val MARKS = Regex("\\p{M}+")

    /** The column a new item of [itemType] lands in: an idea in the backlog, anything else in to do. */
    fun columnFor(itemType: String): String = if (itemType == IDEA) BACKLOG else TODO

    /** How important a priority is; an unknown one counts as normal. */
    fun rank(priority: String): Int = when (priority) {
        URGENT -> 3
        HIGH -> 2
        LOW -> 0
        else -> 1
    }

    /**
     * What moving an item to the board [column] does to a task in [state]: dropped drops it, done
     * finishes it, and any other column reopens a done or dropped one.
     */
    fun moved(column: String, state: TaskState): TaskState = when {
        column == DROPPED -> TaskState.DROPPED
        column == DONE -> TaskState.DONE
        state == TaskState.DONE || state == TaskState.DROPPED -> TaskState.OPEN
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

    /**
     * The board columns of a project with their items in order; a column with nothing in it stays. A
     * dropped item is in [DROPPED], never in the column it is stored in.
     */
    fun board(items: List<TaskItem>): List<ProjectColumn> = BOARD_COLUMNS.map { column ->
        val dropped = column == DROPPED
        ProjectColumn(
            column,
            order(items.filter { !it.deleted && (it.state == TaskState.DROPPED) == dropped && (dropped || it.boardColumn == column) }),
        )
    }

    /**
     * Whether [key] can be a project's key once it is upper-cased: 2 to 6 letters or digits, starting
     * with a letter (contracts/vectors/projects.json 'itemKeys').
     */
    fun isItemKey(key: String?): Boolean =
        key != null &&
            key.length in SHORTEST_ITEM_KEY..LONGEST_ITEM_KEY &&
            key[0].isAsciiLetter() &&
            key.all { it.isAsciiLetter() || it.isAsciiDigit() }

    /**
     * A key for a project called [name]: the capitals of one word (GoalMaker gives GM), the first
     * letters of several (Jsi na tahu gives JNT), or the first three letters of one plain word (Thesis
     * gives THE), at most four characters, with 2, 3 ... added while it is in [taken] (any case). Null
     * when the name has too little to make one from.
     */
    fun suggestItemKey(name: String, taken: Collection<String>): String? {
        val words = words(name).filterNot { it[0].isAsciiDigit() }
        val made = when {
            words.isEmpty() -> ""
            words.size == 1 && words[0].count { it in 'A'..'Z' } >= 2 -> words[0].filter { it in 'A'..'Z' }
            words.size == 1 -> words[0].take(3)
            else -> words.joinToString("") { it.take(1) }
        }
        val key = made.take(LONGEST_SUGGESTED_KEY).uppercase(Locale.ROOT)
        if (key.length < SHORTEST_ITEM_KEY) return null
        val used = taken.map { it.uppercase(Locale.ROOT) }.toSet()
        var candidate = key
        var number = 2
        while (candidate in used) candidate = key + number++
        return candidate.takeIf(::isItemKey)
    }

    /** An item's id as it reads: KEY-number, or #number in a project without a key. */
    fun formatItemId(key: String?, number: Int): String = if (key.isNullOrEmpty()) "#$number" else "$key-$number"

    /** The id an item shows, or null while it has no number yet (the server gives it) or no project. */
    fun itemIdOf(item: TaskItem, project: ProjectItem?): String? {
        val number = item.itemNumber ?: return null
        if (project == null || item.projectId != project.id) return null
        return formatItemId(project.itemKey, number)
    }

    /**
     * Reads an id back, in any case and with spaces around it: KEY-number or #number, the number 1 or
     * more. Null for anything else.
     */
    fun parseItemId(text: String?): ItemId? {
        val trimmed = text?.trim().orEmpty()
        val key: String?
        val digits: String
        if (trimmed.startsWith('#')) {
            key = null
            digits = trimmed.substring(1)
        } else {
            val dash = trimmed.indexOf('-')
            if (dash <= 0) return null
            key = trimmed.substring(0, dash).uppercase(Locale.ROOT)
            digits = trimmed.substring(dash + 1)
            if (!isItemKey(key)) return null
        }
        if (digits.isEmpty() || !digits.all { it.isAsciiDigit() }) return null
        val number = digits.toIntOrNull() ?: return null
        return if (number >= 1) ItemId(key, number) else null
    }

    /**
     * Whether an item of a project keyed [key], numbered [number], is the one [wanted] names. A
     * #number names an item of a project without a key, or, with [inProject] (a search inside one
     * project's board), that project's item.
     */
    fun isItem(wanted: ItemId, key: String?, number: Int?, inProject: Boolean = false): Boolean =
        number == wanted.number &&
            if (wanted.key == null) inProject || key.isNullOrEmpty() else wanted.key.equals(key, ignoreCase = true)

    /**
     * The items of [tasks] that [wanted] names, in their order: items of one of [projects] (by id) with
     * the number and the project's key, or #number for a project without one. A deleted item, or one
     * whose project is gone, has no id.
     */
    fun named(wanted: ItemId, tasks: List<TaskItem>, projects: Map<String, ProjectItem>): List<TaskItem> = tasks.filter { task ->
        val project = task.projectId?.let(projects::get)
        !task.deleted && project != null && !project.deleted && isItem(wanted, project.itemKey, task.itemNumber)
    }

    // The words of a name: its letters and digits, diacritics dropped, split at anything else.
    private fun words(name: String): List<String> =
        MARKS.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotEmpty)

    private fun Char.isAsciiLetter() = this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
