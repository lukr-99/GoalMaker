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
    private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"

    // A daily habit's streak can't reach back further than this many periods.
    private const val MAX_PERIODS = 3700

    /** The weekday bit of [day]: Monday 1, Tuesday 2 ... Sunday 64. */
    fun weekdayBit(day: LocalDate): Int = 1 shl (day.dayOfWeek.value - 1)

    /** Whether [habit] is due on [day]; weekly and monthly habits are due any day. */
    fun isDue(habit: HabitItem, day: LocalDate): Boolean =
        habit.cadence != WEEKDAYS || ((habit.weekdays ?: 0) and weekdayBit(day)) != 0

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

    /** Whether a check-in meets its day: checked, or the day's value reaching the target. Skipped never does. */
    fun dayMet(habit: HabitItem, checkin: HabitCheckin?): Boolean {
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
        return when {
            inPeriod.count { dayMet(habit, it) } >= required(habit) -> HabitPeriodState.MET
            pauses.any { !it.deleted && !it.from.isAfter(end) && (it.until == null || !it.until.isBefore(start)) } -> HabitPeriodState.PAUSED
            inPeriod.any { it.skipped } -> HabitPeriodState.SKIPPED
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

    /** A day of the heatmap: none, paused, skipped, or the day's value against its target. */
    fun heat(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): HabitHeat {
        if (day.isBefore(habit.startsOn) || !isDue(habit, day)) return HabitHeat.None
        if (pauses.any { !it.deleted && !it.from.isAfter(day) && (it.until == null || !it.until.isBefore(day)) }) return HabitHeat.Paused
        val checkin = checkins.firstOrNull { !it.deleted && it.day == day }
        if (checkin?.skipped == true) return HabitHeat.Skipped
        return HabitHeat.Share(share(habit, checkin?.value ?: 0.0))
    }

    /** Today's ring: the day against the target, or the days met so far against N; null when today isn't due. */
    fun ring(habit: HabitItem, today: LocalDate, checkins: List<HabitCheckin>): Double? {
        if (habit.cadence == PER_WEEK || habit.cadence == PER_MONTH) {
            val start = periodStart(habit, today)
            val end = periodEnd(habit, start)
            val met = checkins.count { !it.day.isBefore(start) && !it.day.isAfter(end) && dayMet(habit, it) }
            return (met.toDouble() / required(habit)).coerceAtMost(1.0)
        }
        if (!isDue(habit, today)) return null
        val checkin = checkins.firstOrNull { !it.deleted && !it.skipped && it.day == today }
        return share(habit, checkin?.value ?: 0.0)
    }

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
            .filter { !it.deleted && !it.skipped && it.habitId in serving && !it.day.isBefore(goal.periodStart) && !it.day.isAfter(end) }
            .map(HabitCheckin::value)
    }

    private fun share(habit: HabitItem, value: Double): Double =
        if (habit.measure == CHECK) (if (value >= 1.0) 1.0 else 0.0) else (value / (habit.target ?: 1.0)).coerceIn(0.0, 1.0)

    private fun unitKey(unit: String): String = unit.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)
}
