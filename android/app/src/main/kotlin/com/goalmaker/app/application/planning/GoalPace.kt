package com.goalmaker.app.application.planning

/**
 * Where a goal stands against the share of its period gone by (docs/goals.md), in the order the Goals
 * screen sorts by: the goals that need you first. [id] is how the vectors name it.
 */
enum class GoalPace(val id: String) {
    BEHIND("behind"),
    ON_TRACK("on_track"),
    HIT("hit"),
    DROPPED("dropped"),
    ;

    companion object {
        fun of(id: String?): GoalPace? = entries.firstOrNull { it.id == id }
    }
}
