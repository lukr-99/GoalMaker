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
import kotlinx.serialization.json.intOrNull

/**
 * Tally's synced rows, read from the replica and changed through its outbox (docs/tally.md, ADR 0013):
 * this phone's daily totals, which it rewrites a day at a time, and the owner's own categories and
 * rules. [device] is this install's random id. The methods block on disk, so callers run them off
 * the main thread.
 */
class TallyList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
    private val device: () -> String,
) {
    /**
     * Makes this phone's rows for the planning [day] the [totals] of that day: a row per category, with
     * the id [TallyRules.dayId] gives, and this phone's other rows for the day deleted. Totals of other
     * days are left out. False when nobody is signed in.
     */
    fun rewrite(day: LocalDate, totals: List<TallyTotal>): Boolean {
        val owner = rows.owner() ?: return false
        val device = device()
        replica.inTransaction {
            // The phone never links time to a project (the server refuses it), so a day's totals are
            // added up by category alone.
            val minutes = totals.filter { it.day == day && it.minutes > 0 }
                .groupingBy(TallyTotal::category)
                .fold(0) { sum, total -> sum + total.minutes }
            val kept = minutes.map { (category, sum) ->
                val id = TallyRules.dayId(owner, day, device, category, null)
                val value = sum.coerceAtMost(TallyRules.MAX_MINUTES)
                val existing = replica.get(DAYS, id)
                if (existing == null) {
                    val row = rows.create(
                        DAYS,
                        mapOf(
                            SyncedTable.ID to JsonPrimitive(id),
                            "day" to JsonPrimitive(day.toString()),
                            "device" to JsonPrimitive(device),
                            "device_kind" to JsonPrimitive(TallyRules.PHONE),
                            "category" to JsonPrimitive(category),
                            "minutes" to JsonPrimitive(value),
                        ),
                    )
                    row?.let { replica.queue(DAYS, it) }
                } else if (existing.text(SyncedTable.DELETED_AT) != null || existing.minutes() != value) {
                    replica.queue(DAYS, existing.with("minutes" to JsonPrimitive(value), SyncedTable.DELETED_AT to JsonNull))
                }
                id
            }.toSet()
            replica.all(DAYS)
                .filter { row ->
                    row.text("day") == day.toString() && row.text("device").equals(device, ignoreCase = true) &&
                        row.text(SyncedTable.DELETED_AT) == null && row.text(SyncedTable.ID) !in kept
                }
                .forEach { row -> replica.queue(DAYS, row.with(SyncedTable.DELETED_AT to JsonPrimitive(rows.timestamp()))) }
        }
        requestSync()
        return true
    }

    /** Ticks once at first and again whenever a day, a category or a rule changes, from any device. */
    fun watch(): Flow<Unit> = combine(replica.watch(DAYS), replica.watch(CATEGORIES), replica.watch(RULES)) { _, _, _ -> }

    /** Every device's totals from [from] to [to], both included, by day, device, category and project. */
    fun totals(from: LocalDate, to: LocalDate): List<TallyDay> = replica.all(DAYS)
        .filter { it.text(SyncedTable.DELETED_AT) == null }
        .mapNotNull(::toDay)
        .filter { !it.day.isBefore(from) && !it.day.isAfter(to) }
        .sortedWith(compareBy<TallyDay> { it.day }.thenBy { it.device }.thenBy { it.category }.thenBy { it.project.orEmpty() })

    /** The owner's own categories that aren't deleted, in their order. */
    fun categories(): List<TallyCategory> = replica.all(CATEGORIES)
        .filter { it.text(SyncedTable.DELETED_AT) == null }
        .map { row ->
            TallyCategory(
                id = row.text(SyncedTable.ID).orEmpty(),
                name = row.text("name").orEmpty(),
                color = row.text("color").orEmpty(),
                emoji = row.text("emoji"),
                position = row.position(),
            )
        }
        .sortedWith(compareBy<TallyCategory> { it.position }.thenBy { it.name.lowercase(Locale.ROOT) })

    /** Adds a category of the owner's own at the end. Null without a name or a palette color. */
    fun addCategory(name: String, color: String, emoji: String? = null): TallyCategory? {
        val values = categoryValues(name, color, emoji) ?: return null
        val row = rows.create(CATEGORIES, values + ("position" to JsonPrimitive(nextPosition(CATEGORIES)))) ?: return null
        replica.queue(CATEGORIES, row)
        requestSync()
        return categories().firstOrNull { it.id == row.text(SyncedTable.ID) }
    }

    /** Renames or recolors one of the owner's categories, keeping its id and place. False when it doesn't hold. */
    fun updateCategory(id: String, name: String, color: String, emoji: String? = null): Boolean {
        val values = categoryValues(name, color, emoji) ?: return false
        return change(CATEGORIES, id) { it.putAll(values) }
    }

    /**
     * Deletes one of the owner's categories. Its rules and the time already sorted into it stay; the
     * places name such time as a category that is gone.
     */
    fun deleteCategory(id: String): Boolean = change(CATEGORIES, id) { it[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** The owner's own rules that aren't deleted, in the order they are tried. */
    fun rules(): List<TallyRule> = replica.all(RULES)
        .filter { it.text(SyncedTable.DELETED_AT) == null }
        .sortedBy { it.position() }
        .map { row ->
            TallyRule(
                match = row.text("match").orEmpty(),
                pattern = row.text("pattern").orEmpty(),
                platform = row.text("platform") ?: TallyRules.ANY,
                category = row.text("category").orEmpty(),
                project = row.text("project_id"),
                id = row.text(SyncedTable.ID),
            )
        }

    /**
     * Adds a rule of the owner's own after the others. Null when its match, platform, pattern or category
     * don't hold; a title or folder rule can't be for Android alone, since the phone has neither.
     */
    fun addRule(rule: TallyRule): TallyRule? {
        val values = ruleValues(rule) ?: return null
        val row = rows.create(RULES, values + ("position" to JsonPrimitive(nextPosition(RULES)))) ?: return null
        replica.queue(RULES, row)
        requestSync()
        return rules().firstOrNull { it.id == row.text(SyncedTable.ID) }
    }

    /** Changes one of the owner's rules, keeping its id and its place in the order. False when it doesn't hold. */
    fun updateRule(id: String, rule: TallyRule): Boolean {
        val values = ruleValues(rule) ?: return false
        return change(RULES, id) { it.putAll(values) }
    }

    fun deleteRule(id: String): Boolean = change(RULES, id) { it[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /**
     * Sorts one app, site or folder into [category] in one step (docs/tally.md, "Sorting"): the owner's
     * own rule for exactly that [match] and [pattern] on that [platform] changes its category, or a new
     * rule is added after the others. False when the rule wouldn't hold.
     */
    fun sortInto(match: String, pattern: String, platform: String, category: String): Boolean {
        val same = rules().firstOrNull {
            it.match == match && it.platform == platform && it.pattern.equals(pattern.trim(), ignoreCase = true)
        }
        if (same?.id != null) return updateRule(same.id, same.copy(category = category))
        return addRule(TallyRule(match, pattern, platform, category)) != null
    }

    /**
     * Merges the owner's own category [from] into [into]: its rules sort into [into] from now on, and
     * [from] is deleted. Days already counted keep the category they were counted in. False when [from]
     * isn't one of the owner's categories or the two are the same.
     */
    fun mergeCategory(from: String, into: String): Boolean {
        if (from == into || categories().none { it.id == from }) return false
        rules().filter { it.category == from && it.id != null }.forEach { rule -> updateRule(rule.id!!, rule.copy(category = into)) }
        return deleteCategory(from)
    }

    private fun categoryValues(name: String, color: String, emoji: String?): Map<String, JsonElement>? {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty() || !COLOR.matches(color.trim())) return null
        return mapOf(
            "name" to JsonPrimitive(clean),
            "color" to JsonPrimitive(color.trim()),
            "emoji" to (emoji?.trim()?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonNull),
        )
    }

    private fun ruleValues(rule: TallyRule): Map<String, JsonElement>? {
        val pattern = rule.pattern.trim().take(MAX_PATTERN)
        if (rule.match !in TallyRules.MATCHES || rule.platform !in TallyRules.PLATFORMS || pattern.isEmpty() ||
            rule.category.isBlank() || (rule.match != TallyRules.APP && rule.platform == TallyRules.ANDROID)
        ) {
            return null
        }
        return mapOf(
            "match" to JsonPrimitive(rule.match),
            "pattern" to JsonPrimitive(pattern),
            "platform" to JsonPrimitive(rule.platform),
            "category" to JsonPrimitive(rule.category.trim().take(MAX_CATEGORY)),
            "project_id" to (rule.project?.let(::JsonPrimitive) ?: JsonNull),
        )
    }

    // Edits a row that isn't deleted and queues it; false when there is no such row.
    private fun change(table: String, id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(table, id)?.takeIf { it.text(SyncedTable.DELETED_AT) == null } ?: return false
        val values = LinkedHashMap<String, JsonElement>(row)
        edit(values)
        replica.queue(table, JsonObject(values))
        requestSync()
        return true
    }

    private fun nextPosition(table: String): Int =
        (replica.all(table).filter { it.text(SyncedTable.DELETED_AT) == null }.maxOfOrNull { it.position() } ?: -1) + 1

    private fun toDay(row: JsonObject): TallyDay? {
        val day = row.text("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return TallyDay(
            id = row.text(SyncedTable.ID).orEmpty(),
            day = day,
            device = row.text("device").orEmpty(),
            deviceKind = row.text("device_kind") ?: TallyRules.PHONE,
            category = row.text("category") ?: TallyRules.OTHER,
            project = row.text("project_id"),
            minutes = row.minutes(),
        )
    }

    private companion object {
        const val DAYS = "tally_days"
        const val CATEGORIES = "tally_categories"
        const val RULES = "tally_rules"
        const val MAX_NAME = 40
        const val MAX_CATEGORY = 60
        const val MAX_PATTERN = 200
        val COLOR = Regex("^[a-z][a-z0-9-]{0,23}$")

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
        fun JsonObject.minutes(): Int = (this["minutes"] as? JsonPrimitive)?.intOrNull ?: 0
        fun JsonObject.position(): Int = (this["position"] as? JsonPrimitive)?.intOrNull ?: 0
        fun JsonObject.with(vararg changes: Pair<String, JsonElement>): JsonObject = JsonObject(LinkedHashMap(this).apply { putAll(changes) })
    }
}
