package com.goalmaker.app.domain.settings

/**
 * What the calendar's bottom bar adds when several days are picked (docs/calendar.md): one event from
 * the first picked day to the last, or a copy of the task on each picked day.
 */
enum class PickedDaysAdd {
    ONE_EVENT,
    TASK_ON_EACH,
}
