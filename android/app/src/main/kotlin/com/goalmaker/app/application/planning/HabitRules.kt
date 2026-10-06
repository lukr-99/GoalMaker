package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.NameBasedUuid
import java.time.LocalDate
import java.util.Locale

/**
 * Habit cadences, periods, streaks, the heatmap and today's ring (docs/habits.md,
 * contracts/vectors/habits.json). The check-ins and pauses handed in are the habit's own.
 */
object HabitRules {
    const val DAILY = "daily"
    const val WEEKDAYS = "weekdays"
    const val PER_WEEK = "per_week"
    const val PER_MONTH = "per_month"
    const val CHECK = "check"
    const val COUNT = "count"
    const val AMOUNT = "amount"
    const val AT_LEAST = "at_least"
    const val AT_MOST = "at_most"
    private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"

    // A daily habit's streak can't reach back further than this many periods.
    private const val MAX_PERIODS = 3700

    /** The weekday bit of [day]: Monday 1, Tuesday 2 ... Sunday 64. */
    fun weekdayBit(day: LocalDate): Int = 1 shl (day.dayOfWeek.value - 1)

    /** Whether [habit] is due on [day]; weekly and monthly habits are due any day. */
    fun isDue(habit: HabitItem, day: LocalDate): Boolean =
        habit.cadence != WEEKDAYS || ((habit.weekdays ?: 0) and weekdayBit(day)) != 0

    /**
     * Whether [habit] asks something of [today]: not archived, started, due that day and not paused. A
     * habit kept off Today is still due here, so the Habits page, the Places hub and the counts keep it.
     */
    fun dueToday(habit: HabitItem, today: LocalDate, pauses: List<HabitPause>): Boolean =
        !habit.archived && !today.isBefore(habit.startsOn) && isDue(habit, today) &&
            pauses.none { !it.deleted && !it.from.isAfter(today) && (it.until == null || !it.until.isBefore(today)) }

    /** Whether Today's ring row and the widgets show [habit]: due today and not kept off Today. */
    fun onToday(habit: HabitItem, today: LocalDate, pauses: List<HabitPause>): Boolean =
        habit.showOnToday && dueToday(habit, today, pauses)

    /** The first day of [habit]'s period holding [day]: the day, its week's Monday, or its month's first. */
    fun periodStart(habit: HabitItem, day: LocalDate): LocalDate = when (habit.cadence) {
        PER_WEEK -> day.minusDays((day.dayOfWeek.value - 1).toLong())
        PER_MONTH -> day.withDayOfMonth(1)
        else -> day
    }

    /** The last day of [habit]'s period starting on [start]. */
    fun periodEnd(habit: HabitItem, start: LocalDate): LocalDate = when (habit.cadence) {
        PER_WEEK -> start.plusDays(6)
        PER_MONTH -> start.plusMonths(1).minusDays(1)
        else -> start
    }

    /** Whether the habit's number is a limit rather than something to reach (docs/habits.md). */
    fun isLimit(habit: HabitItem): Boolean = habit.direction == AT_MOST

    /** Whether the habit counts by the week or the month rather than by the day. */
    fun isPeriodic(habit: HabitItem): Boolean = habit.cadence == PER_WEEK || habit.cadence == PER_MONTH

    /**
     * A limit habit's number. For a day: the target, or none at all for a check ("not once"). For a
     * week or a month: how many days a check habit may have (`times`), or the most a count or an
     * amount may add up to (the target). It may be 0.
     */
    fun limit(habit: HabitItem): Double = when {
        isPeriodic(habit) && habit.measure == CHECK -> (habit.times ?: 0).toDouble()
        habit.measure == CHECK -> 0.0
        else -> habit.target ?: 0.0
    }

    /** Whether a value goes over a limit habit's number. A habit to build is never over. */
    fun isOver(habit: HabitItem, value: Double): Boolean = isLimit(habit) && value > limit(habit)

    /**
     * What a limit habit has had in its period up to and including [day]: the day's value, or the week's
     * or month's so far. Skipped and failed check-ins hold nothing.
     */
    fun used(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>): Double {
        val start = periodStart(habit, day)
        return checkins
            .filter { !it.deleted && !it.skipped && !it.failed && !it.day.isBefore(start) && !it.day.isAfter(day) }
            .sumOf(HabitCheckin::value)
    }

    /** Whether the period went over the limit by [day]: what turns the ring and the day red. */
    fun wentOver(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>): Boolean =
        isLimit(habit) && used(habit, day, checkins) > limit(habit)

    /**
     * Whether a check-in meets its day: checked, or the day's value reaching the target. Under a limit, a
     * day nobody logged is met, because nothing was had. Skipped and failed never meet a day.
     */
    fun dayMet(habit: HabitItem, checkin: HabitCheckin?): Boolean {
        if (checkin != null && !checkin.deleted && checkin.failed) return false
        if (isLimit(habit)) return checkin == null || checkin.deleted || (!checkin.skipped && !isOver(habit, checkin.value))
        if (checkin == null || checkin.deleted || checkin.skipped) return false
        return if (habit.measure == CHECK) checkin.value >= 1.0 else checkin.value >= (habit.target ?: Double.MAX_VALUE)
    }

    /** How many days a period needs: one for a day, N for a week or month. */
    fun required(habit: HabitItem): Int = if (habit.cadence == PER_WEEK || habit.cadence == PER_MONTH) habit.times ?: 1 else 1

    /** The state of [habit]'s period starting on [start], seen from [today]. */
    fun state(habit: HabitItem, start: LocalDate, today: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): HabitPeriodState {
        val end = periodEnd(habit, start)
        if (end.isBefore(habit.startsOn)) return HabitPeriodState.NONE
        if ((habit.cadence == DAILY || habit.cadence == WEEKDAYS) && !isDue(habit, start)) return HabitPeriodState.NONE
        val inPeriod = checkins.filter { !it.deleted && !it.day.isBefore(start) && !it.day.isAfter(end) }
        if (isLimit(habit)) {
            // A limit is kept by default, so a pause or a skip comes before the day is judged, and a day
            // over the number is missed the moment it happens, today included.
            return when {
                pauses.any { !it.deleted && !it.from.isAfter(end) && (it.until == null || !it.until.isBefore(start)) } -> HabitPeriodState.PAUSED
                inPeriod.any { it.skipped } -> HabitPeriodState.SKIPPED
                inPeriod.any { it.failed } || isOver(habit, inPeriod.sumOf(HabitCheckin::value)) -> HabitPeriodState.MISSED
                !end.isBefore(today) -> HabitPeriodState.OPEN
                else -> HabitPeriodState.MET
            }
        }
        return when {
            inPeriod.count { dayMet(habit, it) } >= required(habit) -> HabitPeriodState.MET
            pauses.any { !it.deleted && !it.from.isAfter(end) && (it.until == null || !it.until.isBefore(start)) } -> HabitPeriodState.PAUSED
            inPeriod.any { it.skipped } -> HabitPeriodState.SKIPPED
            inPeriod.any { it.failed } -> HabitPeriodState.MISSED
            !end.isBefore(today) -> HabitPeriodState.OPEN
            else -> HabitPeriodState.MISSED
        }
    }

    /** Met periods back from the one holding [today]; open, paused, skipped and none pass, missed ends it. */
    fun streak(habit: HabitItem, today: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): Int {
        var count = 0
        var start = periodStart(habit, today)
        repeat(MAX_PERIODS) {
            if (periodEnd(habit, start).isBefore(habit.startsOn)) return count
            when (state(habit, start, today, checkins, pauses)) {
                HabitPeriodState.MET -> count++
                HabitPeriodState.MISSED -> return count
                else -> Unit
            }
            start = periodStart(habit, start.minusDays(1))
        }
        return count
    }

    /** The starts of [habit]'s periods that touch the days [from] to [to], oldest first. */
    fun periodsBetween(habit: HabitItem, from: LocalDate, to: LocalDate): List<LocalDate> {
        val starts = mutableListOf<LocalDate>()
        var start = periodStart(habit, from)
        while (!start.isAfter(to)) {
            if (!start.isBefore(from) || !periodEnd(habit, start).isBefore(from)) starts += start
            start = periodEnd(habit, start).plusDays(1)
        }
        return starts
    }

    /** A day of the heatmap: none, paused, skipped, over a limit, or the day's value against its target. */
    fun heat(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): HabitHeat {
        if (day.isBefore(habit.startsOn) || !isDue(habit, day)) return HabitHeat.None
        if (pauses.any { !it.deleted && !it.from.isAfter(day) && (it.until == null || !it.until.isBefore(day)) }) return HabitHeat.Paused
        val checkin = checkins.firstOrNull { !it.deleted && it.day == day }
        if (checkin?.skipped == true) return HabitHeat.Skipped
        if (checkin?.failed == true) return if (isLimit(habit)) HabitHeat.Over else HabitHeat.Share(0.0)
        // A limit's heatmap reads the other way round: a clean day is full, the shade fades as the day's
        // or the period's allowance is used, and going over is its own mark.
        if (isLimit(habit)) {
            val had = used(habit, day, checkins)
            return if (isOver(habit, had)) HabitHeat.Over else HabitHeat.Share(1.0 - limitShare(habit, had))
        }
        return HabitHeat.Share(share(habit, checkin?.value ?: 0.0))
    }

    /** Today's ring: the day against the target, or the days met so far against N; null when today isn't due. */
    fun ring(habit: HabitItem, today: LocalDate, checkins: List<HabitCheckin>): Double? {
        // A limit's ring fills with what was had in the day, or in the week or month so far.
        if (isLimit(habit)) {
            if (!isPeriodic(habit) && !isDue(habit, today)) return null
            if (checkins.any { !it.deleted && it.failed && it.day == today }) return 0.0
            return limitShare(habit, used(habit, today, checkins))
        }
        if (habit.cadence == PER_WEEK || habit.cadence == PER_MONTH) {
            val start = periodStart(habit, today)
            val end = periodEnd(habit, start)
            val met = checkins.count { !it.day.isBefore(start) && !it.day.isAfter(end) && dayMet(habit, it) }
            return (met.toDouble() / required(habit)).coerceAtMost(1.0)
        }
        if (!isDue(habit, today)) return null
        val checkin = checkins.firstOrNull { !it.deleted && !it.skipped && it.day == today }
        if (checkin?.failed == true) return 0.0
        return share(habit, checkin?.value ?: 0.0)
    }

    /** The Habits page's group for [habit]: limits, weekly (and monthly) ones, or the ones on days. */
    fun group(habit: HabitItem): HabitGroup = when {
        isLimit(habit) -> HabitGroup.LIMITS
        habit.cadence == PER_WEEK || habit.cadence == PER_MONTH -> HabitGroup.WEEKLY
        else -> HabitGroup.DAYS
    }

    /**
     * Where [habit] stands on [today]: none, paused, skipped, failed, a limit (never done or left), done (the ring is
     * full, or a weekly or monthly habit's check-in today meets its day) or left.
     */
    fun standing(habit: HabitItem, today: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): HabitStanding {
        if (habit.archived || today.isBefore(habit.startsOn) || !isDue(habit, today)) return HabitStanding.NONE
        if (pauses.any { covers(it, today, today) }) return HabitStanding.PAUSED
        val start = periodStart(habit, today)
        val end = periodEnd(habit, start)
        if (checkins.any { !it.deleted && it.skipped && !it.day.isBefore(start) && !it.day.isAfter(end) }) return HabitStanding.SKIPPED
        if (checkins.any { !it.deleted && it.failed && !it.day.isBefore(start) && !it.day.isAfter(end) }) return HabitStanding.FAILED
        if (isLimit(habit)) return HabitStanding.LIMIT
        if ((ring(habit, today, checkins) ?: 0.0) >= 1.0) return HabitStanding.DONE
        val todays = checkins.firstOrNull { !it.deleted && it.day == today }
        if ((habit.cadence == PER_WEEK || habit.cadence == PER_MONTH) && todays != null && dayMet(habit, todays)) return HabitStanding.DONE
        return HabitStanding.LEFT
    }

    /** One day of the week's dots: none, paused, skipped, over, open (today), met or missed. */
    fun dot(habit: HabitItem, day: LocalDate, today: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): HabitDot {
        if (day.isBefore(habit.startsOn) || !isDue(habit, day)) return HabitDot.NONE
        if (pauses.any { covers(it, day, day) }) return HabitDot.PAUSED
        val checkin = checkins.firstOrNull { !it.deleted && it.day == day }
        if (checkin?.skipped == true) return HabitDot.SKIPPED
        if (checkin?.failed == true) return if (isLimit(habit)) HabitDot.OVER else HabitDot.MISSED
        if (isLimit(habit)) {
            return when {
                checkin != null && wentOver(habit, day, checkins) -> HabitDot.OVER
                !day.isBefore(today) -> HabitDot.OPEN
                else -> HabitDot.MET
            }
        }
        return when {
            checkin != null && dayMet(habit, checkin) -> HabitDot.MET
            !day.isBefore(today) -> HabitDot.OPEN
            // A weekly or monthly habit isn't due on any one day, so a day without one misses nothing.
            habit.cadence == PER_WEEK || habit.cadence == PER_MONTH -> HabitDot.NONE
            else -> HabitDot.MISSED
        }
    }

    /** Whether Today shows its "all done" card: none of its habits is left, and at least one is done. */
    fun allDone(standings: List<HabitStanding>): Boolean =
        standings.none { it == HabitStanding.LEFT } && standings.any { it == HabitStanding.DONE }

    /** The id of [habitId]'s one check-in on [day], the same on every device. */
    fun checkinId(habitId: String, day: LocalDate): String =
        NameBasedUuid.of(NAMESPACE, "checkin/${habitId.lowercase(Locale.ROOT)}/$day")

    /**
     * The check-in values that count toward [goal]: of habits serving it, measured by count or amount, in
     * its unit (lowercased, without spaces), not skipped, on a day in its period (story 32).
     */
    fun goalAmounts(goal: GoalItem, habits: List<HabitItem>, checkins: List<HabitCheckin>): List<Double> {
        val unit = goal.unit?.let(::unitKey) ?: return emptyList()
        val end = GoalRules.periodEnd(goal.horizon, goal.periodStart)
        val serving = habits
            .filter { !it.deleted && it.goalId == goal.id && it.measure != CHECK && it.unit?.let(::unitKey) == unit }
            .map(HabitItem::id)
            .toSet()
        return checkins
            .filter { !it.deleted && !it.skipped && !it.failed && it.habitId in serving && !it.day.isBefore(goal.periodStart) && !it.day.isAfter(end) }
            .map(HabitCheckin::value)
    }

    /**
     * What one tap on an amount habit's check-in button logs (docs/habits.md, "One tap"): the rest of the
     * day's target, or null when nothing is left, when the habit isn't an amount or when it is a limit.
     */
    fun fill(habit: HabitItem, value: Double): Double? {
        val target = habit.target
        if (habit.measure != AMOUNT || isLimit(habit) || target == null) return null
        val rest = twoPlaces((target - value).coerceAtLeast(0.0))
        return rest.takeIf { it > 0.0 }
    }

    /** The log sheet's ready taps: a quarter and a half of the target, then the rest when it is another. */
    fun fillPresets(habit: HabitItem, value: Double): List<Double> {
        val target = habit.target
        if (habit.measure != AMOUNT || isLimit(habit) || target == null) return emptyList()
        val presets = listOf(twoPlaces(target / 4), twoPlaces(target / 2))
        val rest = fill(habit, value)
        return if (rest == null || rest in presets) presets else presets + rest
    }

    // Two decimals with halves rounded up, the way every app rounds a fill (0.625 is 0.63).
    private fun twoPlaces(value: Double): Double = Math.floor(value * 100 + 0.5 + 1e-9) / 100

    private fun covers(pause: HabitPause, start: LocalDate, end: LocalDate): Boolean =
        !pause.deleted && !pause.from.isAfter(end) && (pause.until == null || !pause.until.isBefore(start))

    // How much of a limit [had] uses, 0 to 1; with a limit of 0, anything at all uses it up.
    private fun limitShare(habit: HabitItem, had: Double): Double {
        val most = limit(habit)
        return if (most <= 0.0) (if (had > 0.0) 1.0 else 0.0) else (had / most).coerceIn(0.0, 1.0)
    }

    private fun share(habit: HabitItem, value: Double): Double =
        if (habit.measure == CHECK) (if (value >= 1.0) 1.0 else 0.0) else (value / (habit.target ?: 1.0)).coerceIn(0.0, 1.0)

    private fun unitKey(unit: String): String = unit.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)
}
