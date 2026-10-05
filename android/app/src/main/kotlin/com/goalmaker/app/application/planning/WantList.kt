package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The owner's wants and their cooldown thresholds, read from the replica and changed through its
 * outbox (docs/wants.md). A new want takes its cooldown from [WantRules] on the planning day [today].
 * The methods block on disk, so callers run them off the main thread.
 */
class WantList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
    private val today: () -> LocalDate,
) {
    /** Every want that isn't deleted, soonest to cool first, then by title. */
    fun all(): List<WantItem> = replica.all(TABLE).map(::toItem).filterNot(WantItem::deleted).sortedWith(ORDER)

    fun get(id: String): WantItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(WantItem::deleted)

    /** [all], again after every change to a want. Collect it off the main thread. */
    fun watch(): Flow<List<WantItem>> = replica.watch(TABLE).map { all() }

    /** The owner's thresholds, or the defaults while they have none. */
    fun cooldowns(): WantCooldowns {
        val owner = rows.owner() ?: return WantCooldowns.DEFAULT
        val row = replica.get(COOLDOWNS, WantRules.cooldownsId(owner))
            ?.takeIf { it.text(SyncedTable.DELETED_AT) == null } ?: return WantCooldowns.DEFAULT
        return toCooldowns(row)
    }

    /** [cooldowns], again after every change to them. */
    fun watchCooldowns(): Flow<WantCooldowns> = replica.watch(COOLDOWNS).map { cooldowns() }

    /**
     * Keeps new thresholds; false when they don't hold together (days 0 to 365, amounts not negative,
     * the medium threshold not below the small one, a three-letter currency). Wants already cooling
     * keep their days.
     */
    fun setCooldowns(value: WantCooldowns): Boolean {
        val currency = value.currency.trim().uppercase(Locale.ROOT)
        val days = listOf(value.smallDays, value.mediumDays, value.largeDays, value.unpricedDays)
        if (days.any { it !in 0..WantRules.MAX_DAYS } || value.smallUnder < 0 || value.mediumUnder < value.smallUnder ||
            !CURRENCY.matches(currency)
        ) {
            return false
        }
        val owner = rows.owner() ?: return false
        val fields = mapOf(
            "small_under" to JsonPrimitive(value.smallUnder),
            "small_days" to JsonPrimitive(value.smallDays),
            "medium_under" to JsonPrimitive(value.mediumUnder),
            "medium_days" to JsonPrimitive(value.mediumDays),
            "large_days" to JsonPrimitive(value.largeDays),
            "unpriced_days" to JsonPrimitive(value.unpricedDays),
            "currency" to JsonPrimitive(currency),
        )
        val id = WantRules.cooldownsId(owner)
        val existing = replica.get(COOLDOWNS, id)
        val row = if (existing == null) {
            rows.create(COOLDOWNS, fields + (SyncedTable.ID to JsonPrimitive(id))) ?: return false
        } else {
            JsonObject(LinkedHashMap(existing).apply { putAll(fields); put(SyncedTable.DELETED_AT, JsonNull) })
        }
        replica.queue(COOLDOWNS, row)
        requestSync()
        return true
    }

    /** Adds a want with the cooldown its price or the owner's pick gives. Null without a title or a reason. */
    fun add(draft: WantDraft): WantItem? {
        val clean = check(draft) ?: return null
        val addedOn = today()
        val days = WantRules.cooldownDays(clean.price, clean.currency, cooldowns(), clean.pickedDays, clean.kind)
        val row = rows.create(
            TABLE,
            values(clean) + mapOf(
                "cooldown_days" to JsonPrimitive(days),
                "added_on" to JsonPrimitive(addedOn.toString()),
                "cools_until" to JsonPrimitive(WantRules.coolsUntil(addedOn, days).toString()),
                "made_by" to JsonPrimitive(ProjectRules.OWNER),
                "kind" to JsonPrimitive(clean.kind),
                "decision_note" to JsonPrimitive(""),
                "checked_note" to JsonPrimitive(""),
            ),
        ) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** Changes what the want is; its cooldown stays as it was set. */
    fun update(id: String, draft: WantDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(id) { values -> values.putAll(values(clean)) }
    }

    /** Marks a want bought or dropped, with an optional note. */
    fun decide(id: String, decision: String, note: String = ""): Boolean {
        if (decision != WantRules.BOUGHT && decision != WantRules.DROPPED) return false
        return change(id) { values ->
            values["decision"] = JsonPrimitive(decision)
            values["decided_at"] = JsonPrimitive(rows.timestamp())
            values["decision_note"] = JsonPrimitive(note.trim().take(MAX_NOTE))
        }
    }

    /** Takes a decision back; the want is cooling or ready again, as its day says. */
    fun reopen(id: String): Boolean = change(id) { values ->
        values["decision"] = JsonNull
        values["decided_at"] = JsonNull
        values["decision_note"] = JsonPrimitive("")
    }

    fun delete(id: String): Boolean = change(id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Brings a deleted want back, as the undo after a delete does. */
    fun restore(id: String): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it.text(SyncedTable.DELETED_AT) != null } ?: return false
        val values = LinkedHashMap(row)
        values[SyncedTable.DELETED_AT] = JsonNull
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun check(draft: WantDraft): WantDraft? {
        val title = draft.title.trim().take(MAX_TITLE)
        val reason = draft.reason.trim().take(MAX_REASON)
        val need = draft.kind == WantRules.NEED
        // A want says why it is wanted; a need may leave it out (supabase/migrations/0025_wants_needs.sql).
        if (title.isEmpty() || (reason.isEmpty() && !need)) return null
        val currency = draft.currency.trim().uppercase(Locale.ROOT).takeIf(CURRENCY::matches) ?: WantCooldowns.DEFAULT.currency
        return draft.copy(
            title = title,
            reason = reason,
            link = draft.link?.trim()?.take(MAX_LINK)?.takeIf(String::isNotEmpty),
            price = draft.price?.takeIf { it >= 0 && it <= MAX_PRICE },
            currency = currency,
            kind = if (need) WantRules.NEED else WantRules.WANT,
            needBy = draft.needBy.takeIf { need },
        )
    }

    private fun values(draft: WantDraft): Map<String, JsonElement> = mapOf(
        "title" to JsonPrimitive(draft.title),
        "reason" to JsonPrimitive(draft.reason),
        "link" to (draft.link?.let(::JsonPrimitive) ?: JsonNull),
        "price" to (draft.price?.let(::JsonPrimitive) ?: JsonNull),
        "currency" to JsonPrimitive(draft.currency),
        "area_id" to (draft.areaId?.let(::JsonPrimitive) ?: JsonNull),
        "need_by" to (draft.needBy?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
    )

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it.text(SyncedTable.DELETED_AT) == null } ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = WantItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        title = row.text("title").orEmpty(),
        reason = row.text("reason").orEmpty(),
        cooldownDays = (row["cooldown_days"] as? JsonPrimitive)?.intOrNull ?: 0,
        addedOn = row.text("added_on")?.let(LocalDate::parse) ?: LocalDate.ofEpochDay(0),
        coolsUntil = row.text("cools_until")?.let(LocalDate::parse) ?: LocalDate.ofEpochDay(0),
        link = row.text("link"),
        price = (row["price"] as? JsonPrimitive)?.doubleOrNull,
        currency = row.text("currency") ?: WantCooldowns.DEFAULT.currency,
        areaId = row.text("area_id"),
        decision = row.text("decision"),
        decidedAt = row.text("decided_at"),
        decisionNote = row.text("decision_note").orEmpty(),
        checkedPrice = (row["checked_price"] as? JsonPrimitive)?.doubleOrNull,
        checkedAt = row.text("checked_at"),
        checkedNote = row.text("checked_note").orEmpty(),
        madeBy = row.text("made_by") ?: ProjectRules.OWNER,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
        kind = row.text("kind") ?: WantRules.WANT,
        needBy = row.text("need_by")?.let(LocalDate::parse),
    )

    private fun toCooldowns(row: JsonObject): WantCooldowns {
        val base = WantCooldowns.DEFAULT
        fun number(name: String) = (row[name] as? JsonPrimitive)?.doubleOrNull
        fun whole(name: String) = (row[name] as? JsonPrimitive)?.intOrNull
        return WantCooldowns(
            smallUnder = number("small_under") ?: base.smallUnder,
            smallDays = whole("small_days") ?: base.smallDays,
            mediumUnder = number("medium_under") ?: base.mediumUnder,
            mediumDays = whole("medium_days") ?: base.mediumDays,
            largeDays = whole("large_days") ?: base.largeDays,
            unpricedDays = whole("unpriced_days") ?: base.unpricedDays,
            currency = row.text("currency") ?: base.currency,
        )
    }

    private companion object {
        const val TABLE = "wants"
        const val COOLDOWNS = "want_cooldowns"
        const val MAX_TITLE = 200
        const val MAX_REASON = 2000
        const val MAX_LINK = 2000
        const val MAX_NOTE = 2000
        const val MAX_PRICE = 100_000_000.0
        val CURRENCY = Regex("^[A-Z]{3}$")
        val ORDER = compareBy<WantItem> { it.coolsUntil }.thenBy { it.title.lowercase(Locale.ROOT) }

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
