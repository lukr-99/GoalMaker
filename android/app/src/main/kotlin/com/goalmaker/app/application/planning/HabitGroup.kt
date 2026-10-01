package com.goalmaker.app.application.planning

/** The group a habit sits in on the Habits page (docs/habits.md), so a limit never reads as not done. */
enum class HabitGroup(val id: String) {
    /** Every day, or on chosen weekdays. */
    DAYS("days"),

    /** A number of times a week or a month. */
    WEEKLY("weekly"),

    /** A limit to stay under. */
    LIMITS("limits"),
}
