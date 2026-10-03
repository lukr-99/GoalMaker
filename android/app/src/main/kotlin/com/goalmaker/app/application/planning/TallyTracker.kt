package com.goalmaker.app.application.planning

import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow

/**
 * Tally on the phone (docs/tally.md, ADR 0013): reads which apps were in front from the phone's own
 * usage history, sorts them with the owner's rules and then the shipped [defaults], and rewrites this
 * phone's daily totals. Each run reads again from the start of the planning day it last read to, so a
 * day is always counted whole. Package names stay in memory; only minutes per category are written.
 * Blocks on disk, so callers run it off the main thread.
 */
class TallyTracker(
    private val usage: UsageSource,
    private val tally: TallyList,
    private val defaults: List<TallyRule>,
    private val settings: SettingsStore,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) {
    /** Whether the owner has turned Tally on for this phone. */
    val on: StateFlow<Boolean> = settings.tallyOn

    /** Whether the owner has granted usage access, read fresh each time since it can be taken away. */
    fun granted(): Boolean = usage.granted()

    /**
     * Turns Tally on or off. Off forgets how far it read, so turning it on again starts from what the
     * phone still keeps rather than filling in the time it was off with a guess.
     */
    fun turn(on: Boolean) {
        settings.setTallyOn(on)
        if (!on) settings.setTallyReadUntil(null)
    }

    /**
     * Reads the usage history since the last run and rewrites every planning day it touched, from the
     * day the last run ended in to today. Returns those days; none while Tally is off, usage access
     * isn't granted or nobody is signed in, and then nothing is written.
     */
    @Synchronized
    fun track(): List<LocalDate> {
        if (!settings.tallyOn.value || !usage.granted()) return emptyList()
        val until = now()
        val zone = zone()
        val startHour = settings.dayStartHour.value
        val today = PlanningDay.of(LocalDateTime.ofInstant(until, zone), startHour)
        // The first day the phone still keeps whole; the day the history starts in may be cut.
        val earliest = PlanningDay.of(LocalDateTime.ofInstant(until.minus(HISTORY), zone), startHour).plusDays(1)
        val first = (settings.tallyReadUntil()?.let { PlanningDay.of(LocalDateTime.ofInstant(it, zone), startHour) } ?: earliest)
            .coerceIn(earliest, today)
        val from = first.atTime(startHour, 0).atZone(zone).toInstant()
        val stretches = usage.foreground(from, until) ?: return emptyList()
        val own = tally.rules()
        val intervals = stretches.map { stretch ->
            val sort = TallyRules.sortSample(TallySample(TallyRules.ANDROID, stretch.app), own, defaults)
            TallyInterval(LocalDateTime.ofInstant(stretch.start, zone), LocalDateTime.ofInstant(stretch.end, zone), sort.category, sort.project)
        }
        val totals = TallyRules.dayTotals(intervals, startHour)
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        // A day with nothing in it is still rewritten, so a total the phone no longer backs goes away.
        for (day in days) {
            if (!tally.rewrite(day, totals)) return emptyList()
        }
        settings.setTallyReadUntil(until)
        return days
    }

    /**
     * What this phone's own history says was in front from the start of the planning day [from] to the
     * end of [to] (or now), each stretch sorted with the owner's rules and the shipped ones as they are
     * now: the Tally place's hours and apps (docs/tally.md). None while Tally is off or usage access
     * isn't granted. Nothing read here is kept or synced.
     */
    fun stretches(from: LocalDate, to: LocalDate): List<TallyStretch> {
        if (!settings.tallyOn.value || !usage.granted()) return emptyList()
        val zone = zone()
        val startHour = settings.dayStartHour.value
        val since = from.atTime(startHour, 0).atZone(zone).toInstant()
        val until = minOf(now(), to.plusDays(1).atTime(startHour, 0).atZone(zone).toInstant())
        if (!until.isAfter(since)) return emptyList()
        val own = tally.rules()
        return usage.foreground(since, until).orEmpty().map { stretch ->
            TallyStretch(
                start = LocalDateTime.ofInstant(stretch.start, zone),
                end = LocalDateTime.ofInstant(stretch.end, zone),
                app = stretch.app,
                title = null,
                category = TallyRules.sortSample(TallySample(TallyRules.ANDROID, stretch.app), own, defaults).category,
            )
        }
    }

    /** The name an app shows under its icon, or null when the phone doesn't know it. */
    fun appName(app: String): String? = usage.appName(app)

    private companion object {
        // Android keeps about a week of usage events.
        val HISTORY: Duration = Duration.ofDays(7)
    }
}
