package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.HabitDot
import com.goalmaker.app.application.planning.HabitGroup
import com.goalmaker.app.application.planning.HabitHeat
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import java.time.LocalDate

/**
 * A habit as the Habits screen and Today show it (docs/habits.md): today's [ring] (null when today
 * isn't due), the [streak], today's [value] and how many days the period has [met] so far, whether
 * today's period is [skipped] or [paused], where it [standing]s today, the week's [dots] (the seven
 * days up to today, oldest first) and the heatmap from [heatStart] (a Monday) to today.
 */
data class HabitRow(
    val habit: HabitItem,
    val ring: Double?,
    val streak: Int,
    val state: HabitPeriodState,
    val value: Double = 0.0,
    val met: Int = 0,
    val skipped: Boolean = false,
    val paused: Boolean = false,
    val goalTitle: String? = null,
    val heatStart: LocalDate = LocalDate.MIN,
    val heat: List<HabitHeat> = emptyList(),
    val standing: HabitStanding = HabitStanding.NONE,
    val dots: List<HabitDot> = emptyList(),
) {
    /** The habit's number is a limit, so the ring fills with what has been had (docs/habits.md). */
    val isLimit: Boolean get() = HabitRules.isLimit(habit)

    /** Today went over the limit: the ring, the line and the day turn to the danger colour. */
    val isOver: Boolean get() = !skipped && HabitRules.isOver(habit, value)

    /** Today's part is done (contracts/vectors/habits.json, standings): Hide done hides it. */
    val done: Boolean get() = standing == HabitStanding.DONE

    /** Still to do today: Today's count of what is left counts it. */
    val left: Boolean get() = standing == HabitStanding.LEFT

    /** The Habits page's group: every day, weekly or limits. */
    val group: HabitGroup get() = HabitRules.group(habit)

    /** The check-in button takes a tap: the habit is due today, not paused and not archived. */
    val canCheckIn: Boolean get() = standing != HabitStanding.NONE && standing != HabitStanding.PAUSED
}
