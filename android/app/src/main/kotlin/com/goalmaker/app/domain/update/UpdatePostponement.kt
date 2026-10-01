package com.goalmaker.app.domain.update

import java.time.Duration
import java.time.Instant

/**
 * The owner pressed Later on [version]: its mark and its notification stay away until [until]. A
 * newer version is not held back by it.
 */
data class UpdatePostponement(val version: String, val until: Instant) {
    /** Whether [candidate] is still held back at [now]. */
    fun holds(candidate: String, now: Instant): Boolean = candidate == version && now.isBefore(until)

    companion object {
        /** How long Later keeps a version quiet. */
        val LATER: Duration = Duration.ofDays(3)

        /** Later, pressed on [version] at [now]. */
        fun later(version: String, now: Instant) = UpdatePostponement(version, now.plus(LATER))
    }
}
