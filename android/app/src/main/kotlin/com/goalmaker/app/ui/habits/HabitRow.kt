package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.HabitHeat
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import java.time.LocalDate

/**
 * A habit as the Habits screen and Today show it (docs/habits.md): today's [ring] (null when today
 * isn't due), the [streak], today's [value] and how many days the period has [met] so far, whether
 * today's period is [skipped] or [paused], and the heatmap from [heatStart] (a Monday) to today.
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
) {
    /** The habit's number is a limit, so the ring fills with what has been had (docs/habits.md). */
    val isLimit: Boolean get() = HabitRules.isLimit(habit)

    /** Today went over the limit: the ring, the line and the day turn to the danger colour. */
    val isOver: Boolean get() = !skipped && HabitRules.isOver(habit, value)

    /** Today's part is done: the ring is full, or the period is skipped. A limit is never done. */
    val done: Boolean get() = skipped || (!isLimit && (ring ?: 0.0) >= 1.0)
}
