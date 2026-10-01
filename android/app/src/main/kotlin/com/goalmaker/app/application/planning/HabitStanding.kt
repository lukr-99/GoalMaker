package com.goalmaker.app.application.planning

/** Where a habit stands today (docs/habits.md), in the order the rules decide it. */
enum class HabitStanding(val id: String) {
    /** Archived, not started yet, or not due today. */
    NONE("none"),

    /** A pause covers today. */
    PAUSED("paused"),

    /** The period holding today is skipped. */
    SKIPPED("skipped"),

    /** A limit: never done and never left, so it never reads as not done. */
    LIMIT("limit"),

    /** Today's part is done: Hide done hides it. */
    DONE("done"),

    /** Still to do today: Today's count of what is left counts it. */
    LEFT("left"),
}
