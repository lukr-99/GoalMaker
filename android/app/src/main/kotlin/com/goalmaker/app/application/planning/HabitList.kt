package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The owner's habits, their check-ins and pauses, read from the replica and changed through its outbox
 * (docs/habits.md). A day's check-in has the id [HabitRules.checkinId] gives it, so both devices write
 * the same row. The methods block on disk, so callers run them off the main thread.
 */
class HabitList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every habit that isn't deleted, in the owner's order, archived ones last. */
    fun all(): List<HabitItem> = replica.all(TABLE).map(::toItem).filterNot(HabitItem::deleted).sortedWith(ORDER)

    fun find(id: String): HabitItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(HabitItem::deleted)

    /** The habits with every check-in and pause that isn't deleted. */
    fun read(): HabitData = HabitData(
        habits = all(),
        checkins = replica.all(CHECKINS).map(::toCheckin).filterNot(HabitCheckin::deleted),
        pauses = replica.all(PAUSES).map(::toPause).filterNot(HabitPause::deleted),
    )

    /** [read], again after every change to habits, check-ins or pauses. Collect it off the main thread. */
    fun watch(): Flow<HabitData> =
        combine(replica.watch(TABLE), replica.watch(CHECKINS), replica.watch(PAUSES)) { _, _, _ -> read() }

    /** Adds a habit at the end. Null when the draft isn't one the server would keep (see [check]). */
    fun add(draft: HabitDraft): HabitItem? {
        val clean = check(draft) ?: return null
        val position = (all().maxOfOrNull(HabitItem::position) ?: -1.0) + 1.0
        val row = rows.create(TABLE, values(clean) + ("starts_on" to JsonPrimitive(clean.startsOn.toString())) + ("position" to JsonPrimitive(position)))
            ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** Changes a habit to what [draft] says; its start stays. False when the draft isn't valid or the habit is gone. */
    fun update(id: String, draft: HabitDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(TABLE, id) { values -> values.putAll(values(clean)) }
    }

    /** Archives a habit (off Today and the list, kept with its history) or brings it back. */
    fun setArchived(id: String, archived: Boolean): Boolean = change(TABLE, id) { values ->
        values["archived_at"] = if (archived) JsonPrimitive(rows.timestamp()) else JsonNull
    }

    /** Deletes a habit softly; the server takes its check-ins and pauses with it. */
    fun delete(id: String): Boolean = change(TABLE, id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /**
     * Adds [amount] to [day]'s value (a check sets it to 1) and returns the new value; a check-in that
     * said skipped counts again. Null when the habit is gone or the amount isn't positive.
     */
    fun checkIn(habitId: String, day: LocalDate, amount: Double = 1.0): Double? {
        val habit = find(habitId) ?: return null
        if (amount <= 0.0 || !amount.isFinite()) return null
        val before = checkinOn(habitId, day)?.takeUnless(HabitCheckin::skipped)?.value ?: 0.0
        val value = if (habit.measure == HabitRules.CHECK) 1.0 else before + amount
        write(habitId, day, value, skipped = false)
        return value
    }

    /**
     * One tap on a habit's ring for [day]: a check toggles, a count adds one. False for an amount, which
     * asks for its value, and when the habit is gone.
     */
    fun tap(habitId: String, day: LocalDate): Boolean {
        val habit = find(habitId) ?: return false
        return when (habit.measure) {
            HabitRules.CHECK -> {
                val checked = (checkinOn(habitId, day)?.takeUnless(HabitCheckin::skipped)?.value ?: 0.0) >= 1.0
                write(habitId, day, if (checked) 0.0 else 1.0, skipped = false)
                true
            }
            HabitRules.COUNT -> checkIn(habitId, day) != null
            else -> false
        }
    }

    /** Sets [day]'s value exactly: 0 takes a check back, and an undo puts the old value back. */
    fun setValue(habitId: String, day: LocalDate, value: Double): Boolean {
        if (find(habitId) == null || value < 0.0 || !value.isFinite()) return false
        write(habitId, day, value, skipped = false)
        return true
    }

    /** Marks the period holding [day] skipped (sick, travelling), or takes the skip back. */
    fun skip(habitId: String, day: LocalDate, skipped: Boolean = true): Boolean {
        if (find(habitId) == null) return false
        val value = checkinOn(habitId, day)?.value ?: 0.0
        write(habitId, day, if (skipped) 0.0 else value, skipped)
        return true
    }

    /** Pauses a habit from [from] until it resumes; nothing when it is already paused then. */
    fun pause(habitId: String, from: LocalDate): Boolean {
        if (find(habitId) == null || openPause(habitId, from) != null) return false
        val row = rows.create(
            PAUSES,
            mapOf("habit_id" to JsonPrimitive(habitId), "starts_on" to JsonPrimitive(from.toString()), "ends_on" to JsonNull),
        ) ?: return false
        replica.queue(PAUSES, row)
        requestSync()
        return true
    }

    /**
     * Resumes a habit on [day]: the pause covering it ends the day before, or goes away when it started
     * that day. The pause stays otherwise, so old streaks still read right.
     */
    fun resume(habitId: String, day: LocalDate): Boolean {
        val pause = openPause(habitId, day) ?: return false
        return if (pause.from >= day) {
            change(PAUSES, pause.id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }
        } else {
            change(PAUSES, pause.id) { values -> values["ends_on"] = JsonPrimitive(day.minusDays(1).toString()) }
        }
    }

    // The pause covering [day], or one that starts later and has no end.
    private fun openPause(habitId: String, day: LocalDate): HabitPause? = replica.all(PAUSES).map(::toPause)
        .filter { !it.deleted && it.habitId == habitId }
        .firstOrNull { (!it.from.isAfter(day) && (it.until == null || !it.until.isBefore(day))) || (it.from.isAfter(day) && it.until == null) }

    private fun checkinOn(habitId: String, day: LocalDate): HabitCheckin? =
        replica.get(CHECKINS, HabitRules.checkinId(habitId, day))?.let(::toCheckin)?.takeUnless(HabitCheckin::deleted)

    private fun write(habitId: String, day: LocalDate, value: Double, skipped: Boolean) {
        val id = HabitRules.checkinId(habitId, day)
        val fields = mapOf("value" to JsonPrimitive(value), "skipped" to JsonPrimitive(skipped))
        val existing = replica.get(CHECKINS, id)
        val row = if (existing != null) {
            JsonObject(existing + fields + (SyncedTable.DELETED_AT to JsonNull))
        } else {
            rows.create(CHECKINS, fields + mapOf(SyncedTable.ID to JsonPrimitive(id), "habit_id" to JsonPrimitive(habitId), "day" to JsonPrimitive(day.toString())))
                ?: return
        }
        replica.queue(CHECKINS, row)
        requestSync()
    }

    // The draft as the server will take it, or null: a name, a cadence with the days it needs, and a
    // positive target for a count or an amount (supabase/migrations/0010_habits.sql).
    private fun check(draft: HabitDraft): HabitDraft? {
        val name = draft.name.trim().take(MAX_NAME)
        if (name.isEmpty()) return null
        val weekdays = draft.weekdays?.takeIf { draft.cadence == HabitRules.WEEKDAYS }
        val times = draft.times?.takeIf { draft.cadence == HabitRules.PER_WEEK || draft.cadence == HabitRules.PER_MONTH }
        when (draft.cadence) {
            HabitRules.DAILY -> Unit
            HabitRules.WEEKDAYS -> if (weekdays == null || weekdays !in 1..127) return null
            HabitRules.PER_WEEK -> if (times == null || times !in 1..7) return null
            HabitRules.PER_MONTH -> if (times == null || times !in 1..31) return null
            else -> return null
        }
        if (draft.measure !in setOf(HabitRules.CHECK, HabitRules.COUNT, HabitRules.AMOUNT)) return null
        val counted = draft.measure != HabitRules.CHECK
        val target = draft.target?.takeIf { counted }
        if (counted && (target == null || target <= 0.0 || !target.isFinite())) return null
        return draft.copy(
            name = name,
            weekdays = weekdays,
            times = times,
            target = target,
            unit = draft.unit?.trim()?.takeIf { counted && it.isNotEmpty() }?.take(MAX_UNIT),
            emoji = draft.emoji?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_EMOJI),
            goalId = draft.goalId?.takeIf(String::isNotEmpty),
        )
    }

    private fun values(draft: HabitDraft): Map<String, JsonElement> = mapOf(
        "name" to JsonPrimitive(draft.name),
        "emoji" to (draft.emoji?.let(::JsonPrimitive) ?: JsonNull),
        "cadence" to JsonPrimitive(draft.cadence),
        "weekdays" to (draft.weekdays?.let(::JsonPrimitive) ?: JsonNull),
        "times" to (draft.times?.let(::JsonPrimitive) ?: JsonNull),
        "measure" to JsonPrimitive(draft.measure),
        "target" to (draft.target?.let(::JsonPrimitive) ?: JsonNull),
        "unit" to (draft.unit?.let(::JsonPrimitive) ?: JsonNull),
        "goal_id" to (draft.goalId?.let(::JsonPrimitive) ?: JsonNull),
    )

    private fun change(table: String, id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(table, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(table, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = HabitItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        name = row.text("name").orEmpty(),
        startsOn = row.text("starts_on")?.let(LocalDate::parse) ?: LocalDate.ofEpochDay(0),
        cadence = row.text("cadence") ?: HabitRules.DAILY,
        weekdays = (row["weekdays"] as? JsonPrimitive)?.intOrNull,
        times = (row["times"] as? JsonPrimitive)?.intOrNull,
        measure = row.text("measure") ?: HabitRules.CHECK,
        target = (row["target"] as? JsonPrimitive)?.doubleOrNull,
        unit = row.text("unit"),
        emoji = row.text("emoji"),
        goalId = row.text("goal_id"),
        archived = row.text("archived_at") != null,
        position = (row["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private fun toCheckin(row: JsonObject) = HabitCheckin(
        id = row.text(SyncedTable.ID).orEmpty(),
        habitId = row.text("habit_id").orEmpty(),
        day = row.text("day")?.let(LocalDate::parse) ?: LocalDate.ofEpochDay(0),
        value = (row["value"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        skipped = (row["skipped"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.intOrNull == 1) } ?: false,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private fun toPause(row: JsonObject) = HabitPause(
        id = row.text(SyncedTable.ID).orEmpty(),
        habitId = row.text("habit_id").orEmpty(),
        from = row.text("starts_on")?.let(LocalDate::parse) ?: LocalDate.ofEpochDay(0),
        until = row.text("ends_on")?.let(LocalDate::parse),
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private companion object {
        const val TABLE = "habits"
        const val CHECKINS = "habit_checkins"
        const val PAUSES = "habit_pauses"
        const val MAX_NAME = 100
        const val MAX_EMOJI = 16
        const val MAX_UNIT = 20
        val ORDER = compareBy<HabitItem> { it.archived }
            .thenBy(HabitItem::position)
            .thenBy { it.name.lowercase(Locale.ROOT) }

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
