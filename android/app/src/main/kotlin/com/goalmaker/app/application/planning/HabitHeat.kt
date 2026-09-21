package com.goalmaker.app.application.planning

/** One day of a habit's heatmap (docs/habits.md). */
sealed interface HabitHeat {
    /** Before the habit starts, or a day it isn't due. */
    data object None : HabitHeat

    data object Paused : HabitHeat

    data object Skipped : HabitHeat

    /** A day that went over a limit habit's number. */
    data object Over : HabitHeat

    /** The day's value against its target, 0 to 1. */
    data class Share(val fraction: Double) : HabitHeat
}
