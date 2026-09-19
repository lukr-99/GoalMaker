package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The owner's goals and the amounts logged on them, read from the replica and changed through its
 * outbox (docs/goals.md). The methods block on disk, so callers run them off the main thread.
 */
class GoalList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every goal that isn't deleted, longest period first, then by period, position and title. */
    fun all(): List<GoalItem> = replica.all(TABLE).map(::toItem).filterNot(GoalItem::deleted).sortedWith(ORDER)

    fun find(id: String): GoalItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(GoalItem::deleted)

    /** The amounts logged on every goal, not deleted. */
    fun entries(): List<GoalEntryItem> = replica.all(ENTRIES).map(::toEntry).filterNot(GoalEntryItem::deleted)

    /** [all] and [entries], again after every change to either. Collect it off the main thread. */
    fun watch(): Flow<Pair<List<GoalItem>, List<GoalEntryItem>>> =
        combine(replica.watch(TABLE), replica.watch(ENTRIES)) { _, _ -> all() to entries() }

    /** Adds a goal. Null when the draft isn't one the server would keep (see [check]). */
    fun add(draft: GoalDraft): GoalItem? {
        val clean = check(draft) ?: return null
        val siblings = all().count { it.horizon == clean.horizon && it.periodStart == clean.periodStart }
        val row = rows.create(TABLE, values(clean) + ("status" to JsonPrimitive(GoalRules.OPEN)) + ("position" to JsonPrimitive(siblings.toDouble())))
            ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** Changes a goal to what [draft] says. False when the draft isn't valid or the goal is gone. */
    fun update(id: String, draft: GoalDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(id) { values -> values.putAll(values(clean)) }
    }

    /** Open, done (with the time) or dropped. */
    fun setStatus(id: String, status: String): Boolean {
        if (status !in setOf(GoalRules.OPEN, GoalRules.DONE, GoalRules.DROPPED)) return false
        return change(id) { values ->
            values["status"] = JsonPrimitive(status)
            values["completed_at"] = if (status == GoalRules.DONE) JsonPrimitive(rows.timestamp()) else JsonNull
        }
    }

    /** Deletes a goal softly. Tasks and child goals that pointed at it just have no goal from then on. */
    fun delete(id: String): Boolean = change(id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Logs [amount] on a numeric goal for [day] ("+5 km"); a negative amount corrects. Null for zero. */
    fun logAmount(goalId: String, day: LocalDate, amount: Double): GoalEntryItem? {
        if (amount == 0.0 || amount.isNaN() || amount.isInfinite() || find(goalId) == null) return null
        val row = rows.create(
            ENTRIES,
            mapOf("goal_id" to JsonPrimitive(goalId), "day" to JsonPrimitive(day.toString()), "amount" to JsonPrimitive(amount)),
        ) ?: return null
        replica.queue(ENTRIES, row)
        requestSync()
        return toEntry(row)
    }

    /**
     * Copies the goals of the [horizon] period before the one starting on [start] into it (docs/goals.md)
     * and returns how many were made. Nothing when the period already has goals.
     */
    fun copyPrevious(horizon: GoalHorizon, start: LocalDate): Int {
        val goals = all()
        if (goals.any { it.horizon == horizon && it.periodStart == start }) return 0
        val previous = GoalRules.periodStart(horizon, start.minusDays(1))
        val copies = GoalRules.copies(
            goals.filter { it.horizon == horizon && it.periodStart == previous },
            goals.associateBy(GoalItem::id),
            horizon,
            start,
        )
        replica.inTransaction {
            copies.forEach { copy ->
                add(GoalDraft(copy.title, horizon, start, copy.mode, copy.emoji, copy.parentId, copy.target, copy.unit))
            }
        }
        return copies.size
    }

    // The draft as the server will take it, or null: a title, a numeric goal with a positive target.
    private fun check(draft: GoalDraft): GoalDraft? {
        val title = draft.title.trim().take(MAX_TITLE)
        if (title.isEmpty()) return null
        if (draft.mode !in setOf(GoalRules.MODE_DONE, GoalRules.MODE_TASKS, GoalRules.MODE_NUMBER)) return null
        val number = draft.mode == GoalRules.MODE_NUMBER
        val target = draft.target?.takeIf { number }
        if (number && (target == null || target <= 0.0 || target.isNaN() || target.isInfinite())) return null
        val start = GoalRules.periodStart(draft.horizon, draft.periodStart)
        val parent = draft.parentId?.let(::find)?.takeIf { GoalRules.canServe(draft.horizon, start, it.horizon, it.periodStart) }
        return draft.copy(
            title = title,
            periodStart = start,
            emoji = draft.emoji?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_EMOJI),
            parentId = parent?.id,
            target = target,
            unit = draft.unit?.trim()?.takeIf { number && it.isNotEmpty() }?.take(MAX_UNIT),
        )
    }

    private fun values(draft: GoalDraft): Map<String, JsonElement> = mapOf(
        "title" to JsonPrimitive(draft.title),
        "emoji" to (draft.emoji?.let(::JsonPrimitive) ?: JsonNull),
        "horizon" to JsonPrimitive(draft.horizon.id),
        "period_start" to JsonPrimitive(draft.periodStart.toString()),
        "parent_id" to (draft.parentId?.let(::JsonPrimitive) ?: JsonNull),
        "progress_mode" to JsonPrimitive(draft.mode),
        "target" to (draft.target?.let(::JsonPrimitive) ?: JsonNull),
        "unit" to (draft.unit?.let(::JsonPrimitive) ?: JsonNull),
    )

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = GoalItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        title = row.text("title").orEmpty(),
        horizon = GoalHorizon.of(row.text("horizon")) ?: GoalHorizon.WEEK,
        periodStart = row.text("period_start")?.let(LocalDate::parse) ?: LocalDate.EPOCH,
        mode = row.text("progress_mode") ?: GoalRules.MODE_DONE,
        status = row.text("status") ?: GoalRules.OPEN,
        emoji = row.text("emoji"),
        parentId = row.text("parent_id"),
        target = (row["target"] as? JsonPrimitive)?.doubleOrNull,
        unit = row.text("unit"),
        completedAt = row.text("completed_at"),
        position = (row["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private fun toEntry(row: JsonObject) = GoalEntryItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        goalId = row.text("goal_id").orEmpty(),
        day = row.text("day")?.let(LocalDate::parse) ?: LocalDate.EPOCH,
        amount = (row["amount"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private companion object {
        const val TABLE = "goals"
        const val ENTRIES = "goal_entries"
        const val MAX_TITLE = 200
        const val MAX_EMOJI = 16
        const val MAX_UNIT = 20
        val ORDER = compareByDescending<GoalItem> { it.horizon.rank }
            .thenBy(GoalItem::periodStart)
            .thenBy(GoalItem::position)
            .thenBy { it.title.lowercase(Locale.ROOT) }

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
