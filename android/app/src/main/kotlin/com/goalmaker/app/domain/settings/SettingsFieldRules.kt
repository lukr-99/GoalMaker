package com.goalmaker.app.domain.settings

import java.time.LocalTime

/**
 * What the Settings text fields accept. A field checks its text when the owner presses Enter or
 * leaves it, never on every key, and a value these refuse is never saved.
 */
object SettingsFieldRules {
    private val clock = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")
    private val address = Regex("""^https?://[^\s/:?#]+(:\d{1,5})?(/\S*)?$""", RegexOption.IGNORE_CASE)

    /** A time of day written as 22:00 or 7:30, or null when it is not one. */
    fun time(text: String): LocalTime? {
        val match = clock.matchEntire(text.trim()) ?: return null
        return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    /** Whether [text] is an http or https address with a host, as a backend URL has to be. */
    fun backendUrl(text: String): Boolean = address.matches(text.trim())
}
