package com.goalmaker.app.domain.planning

import java.time.LocalDateTime

/** The snoozes every reminder notification offers (docs/reminders.md). */
enum class Snooze {
    TEN_MINUTES,
    ONE_HOUR,
    TOMORROW_MORNING,
    ;

    /**
     * When the reminder comes back. "Tomorrow morning" counts from the planning day, so snoozing
     * before the day starts brings it back that same morning.
     */
    fun target(now: LocalDateTime, dayStartHour: Int = PlanningDay.DEFAULT_START_HOUR): LocalDateTime = when (this) {
        TEN_MINUTES -> now.plusMinutes(10)
        ONE_HOUR -> now.plusHours(1)
        TOMORROW_MORNING -> PlanningDay.of(now, dayStartHour).plusDays(1).atTime(MORNING_HOUR, 0)
    }

    companion object {
        /** The hour "tomorrow morning" means. */
        const val MORNING_HOUR = 8
    }
}
