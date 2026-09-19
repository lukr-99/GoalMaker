package com.goalmaker.app.application.planning

/** Where one period of a habit stands (docs/habits.md), in the order the rules decide it. */
enum class HabitPeriodState(val id: String) {
    /** Before the habit starts, or a day it isn't due. */
    NONE("none"),

    /** Enough days in it were met. */
    MET("met"),

    /** A pause covers one of its days. */
    PAUSED("paused"),

    /** A check-in in it says skipped. */
    SKIPPED("skipped"),

    /** It hasn't ended yet. */
    OPEN("open"),
    MISSED("missed"),
}
