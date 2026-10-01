package com.goalmaker.app.ui.widget

/** What a Motivation widget shows: the owner's own words, or their current goals. */
enum class MotivationMode(val id: String) {
    TEXT("text"),
    GOALS("goals"),
    ;

    companion object {
        fun of(id: String?): MotivationMode? = entries.firstOrNull { it.id == id }
    }
}
