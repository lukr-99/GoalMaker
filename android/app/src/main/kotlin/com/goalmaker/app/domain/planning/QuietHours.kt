package com.goalmaker.app.domain.planning

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The daily window that holds ordinary reminders back (docs/reminders.md). The window may cross
 * midnight; one whose [start] equals its [end] is off.
 */
data class QuietHours(val start: LocalTime, val end: LocalTime) {
    val off: Boolean get() = start == end

    /** Whether [time] falls in the window. The start counts as inside, the end as outside. */
    fun covers(time: LocalTime): Boolean = when {
        off -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }

    /**
     * When a reminder due at [at] actually arrives: its own time when the window is off, when it is
     * [important], or when it falls outside; otherwise the end of the window.
     */
    fun release(at: LocalDateTime, important: Boolean): LocalDateTime {
        if (important || !covers(at.toLocalTime())) return at
        val sameDay = at.toLocalDate().atTime(end)
        return if (sameDay > at) sameDay else at.toLocalDate().plusDays(1).atTime(end)
    }

    companion object {
        /** No quiet hours, which is what a device starts with. */
        val OFF = QuietHours(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)
    }
}
