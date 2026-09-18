package com.goalmaker.app.application.planning

import java.time.LocalDateTime

/** Arms the device's one reminder alarm (docs/reminders.md). Implemented by the platform. */
interface ReminderScheduler {
    /** Replaces whatever was armed with an alarm for [at]. */
    fun armAt(at: LocalDateTime)

    /** Takes the armed alarm away, because nothing is waiting. */
    fun cancel()
}
