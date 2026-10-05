package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.NameBasedUuid
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Wants and their cooldowns (docs/wants.md), pinned by contracts/vectors/wants.json, which the
 * Windows app and the connector run too: the cooldown a new want gets from its price, the day it
 * cools, where it stands on a planning day, which wants a day's notification names, and the stats.
 */
object WantRules {
    const val BOUGHT = "bought"
    const val DROPPED = "dropped"

    /** What a row is (supabase/migrations/0025_wants_needs.sql): a want to wait out, or a need to buy. */
    const val WANT = "want"
    const val NEED = "need"

    /** The composer command that opens the Wants place with a new want (`/want Trail shoes`). */
    const val COMMAND = "want"

    /** The most days a picked cooldown can be. */
    const val MAX_DAYS = 365

    private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"

    /** The id of [owner]'s one row of thresholds, the same on every device. */
    fun cooldownsId(owner: String): String = NameBasedUuid.of(NAMESPACE, "want-cooldowns/${owner.lowercase(Locale.ROOT)}")

    /** The days a new want waits: none for a need, [picked] when the owner chose, otherwise by its price. */
    fun cooldownDays(price: Double?, currency: String, cooldowns: WantCooldowns, picked: Int? = null, kind: String = WANT): Int {
        if (kind == NEED) return 0
        if (picked != null) return picked.coerceIn(0, MAX_DAYS)
        if (price == null || currency != cooldowns.currency) return cooldowns.unpricedDays
        return when {
            price < cooldowns.smallUnder -> cooldowns.smallDays
            price < cooldowns.mediumUnder -> cooldowns.mediumDays
            else -> cooldowns.largeDays
        }
    }

    fun coolsUntil(addedOn: LocalDate, days: Int): LocalDate = addedOn.plusDays(days.toLong())

    /** Where [want] stands on the planning day [today]; null for a deleted want. */
    fun state(want: WantItem, today: LocalDate): WantState? = when {
        want.deleted -> null
        want.decision != null -> WantState.DECIDED
        !today.isBefore(want.coolsUntil) -> WantState.READY
        else -> WantState.COOLING
    }

    /** How far the cooldown has run on [today], 0 to 1, for the ring. */
    fun progress(want: WantItem, today: LocalDate): Double {
        if (want.decision != null) return 1.0
        val total = ChronoUnit.DAYS.between(want.addedOn, want.coolsUntil)
        if (total <= 0) return 1.0
        val elapsed = ChronoUnit.DAYS.between(want.addedOn, today)
        return (elapsed.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /**
     * The wants a notification on [today] names: undecided ones that became ready after [lastNotified]
     * and by today, or only today's when there was no notification before; oldest first, then by title.
     */
    fun ready(wants: List<WantItem>, lastNotified: LocalDate?, today: LocalDate): List<WantItem> = wants
        .filter { want ->
            !want.deleted && want.kind != NEED && want.decision == null && !want.coolsUntil.isAfter(today) &&
                (if (lastNotified == null) want.coolsUntil == today else want.coolsUntil.isAfter(lastNotified))
        }
        .sortedWith(compareBy<WantItem> { it.coolsUntil }.thenBy { it.title.lowercase(Locale.ROOT) })

    /** Bought and dropped, and the dropped prices in [currency] added up; deleted wants never count. */
    fun stats(wants: List<WantItem>, currency: String): WantStats {
        val kept = wants.filter { !it.deleted && it.kind != NEED }
        val dropped = kept.filter { it.decision == DROPPED }
        return WantStats(
            bought = kept.count { it.decision == BOUGHT },
            dropped = dropped.size,
            notSpent = dropped.filter { it.currency == currency }.sumOf { it.price ?: 0.0 },
        )
    }

    /** The open needs: by the day they are needed by (none last), then when they were added, then title. */
    fun needs(wants: List<WantItem>): List<WantItem> = wants
        .filter { !it.deleted && it.kind == NEED && it.decision == null }
        .sortedWith(
            compareBy<WantItem>({ it.needBy == null }, { it.needBy }, { it.addedOn }, { it.title.lowercase(Locale.ROOT) }),
        )

    /** Whether an open need's day passed before the planning day [today]. */
    fun needLate(want: WantItem, today: LocalDate): Boolean =
        want.kind == NEED && want.decision == null && want.needBy?.isBefore(today) == true
}
