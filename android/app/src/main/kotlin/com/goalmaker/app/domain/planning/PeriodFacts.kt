package com.goalmaker.app.domain.planning

/**
 * What a review period looked like, as the reactive prompts read it (docs/reviews.md): the tasks
 * done against the [averageDone] of the periods before it, the goals with where they stand against
 * where they should be, the habits with the periods they missed, and the tasks that kept moving.
 */
data class PeriodFacts(
    val doneTasks: Int = 0,
    val averageDone: Double = 0.0,
    val goals: List<GoalFact> = emptyList(),
    val habits: List<HabitFact> = emptyList(),
    val tasks: List<TaskFact> = emptyList(),
) {
    /** A goal's progress ([fraction], 0 to 1) against the [expected] share of its period gone by. */
    data class GoalFact(val title: String, val fraction: Double, val expected: Double)

    /** A habit's [missed] periods out of [periods], and the streak it is on now. */
    data class HabitFact(val name: String, val missed: Int, val periods: Int, val streak: Int)

    /** A task and how many times it was moved to another day. */
    data class TaskFact(val title: String, val moves: Int)
}
