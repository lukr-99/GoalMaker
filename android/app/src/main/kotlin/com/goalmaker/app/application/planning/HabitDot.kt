package com.goalmaker.app.application.planning

/** One day of the week's dots on a habit card (docs/habits.md). */
enum class HabitDot(val id: String) {
    /** Before the habit starts, a day it isn't due, or a weekly habit's day without a check-in. */
    NONE("none"),
    PAUSED("paused"),
    SKIPPED("skipped"),

    /** A limit's day that went over the number. */
    OVER("over"),

    /** Today, still to come. */
    OPEN("open"),
    MET("met"),
    MISSED("missed"),
}
