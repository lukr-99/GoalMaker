package com.goalmaker.app.application.planning

/** Where a want stands on a planning day (docs/wants.md); [id] is the word the vectors use. */
enum class WantState(val id: String) {
    /** Before the day it cools. */
    COOLING("cooling"),

    /** On or after the day it cools, and not decided yet. */
    READY("ready"),

    /** Bought or dropped, whatever the day. */
    DECIDED("decided"),
}
