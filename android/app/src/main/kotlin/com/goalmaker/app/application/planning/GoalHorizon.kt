package com.goalmaker.app.application.planning

/** The period a goal belongs to, longest first; [id] is how the server and the vectors name it. */
enum class GoalHorizon(val id: String, val rank: Int) {
    YEAR("year", 3),
    MONTH("month", 2),
    WEEK("week", 1),
    DAY("day", 0),
    ;

    companion object {
        fun of(id: String?): GoalHorizon? = entries.firstOrNull { it.id == id }
    }
}
