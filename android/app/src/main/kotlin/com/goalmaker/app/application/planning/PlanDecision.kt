package com.goalmaker.app.application.planning

/** What the Plan tomorrow ritual shows for a task (docs/plan-tomorrow.md). */
enum class PlanDecision {
    /** Open and planned today or earlier: still needs a decision. */
    UNDECIDED,
    TOMORROW,

    /** Planned for a day after tomorrow. */
    LATER,

    /** Open with no day: changed elsewhere, but decided. */
    UNPLANNED,
    DONE,
    DROPPED,
}
