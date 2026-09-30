package com.goalmaker.app.domain.navigation

/**
 * Pinned places and the Places hub (ADR 0014, docs/design/spec.md, Navigation), pinned by
 * contracts/vectors/navigation.json, which the Windows app runs too. A place is a top-level
 * screen named by its id; Settings is not one.
 */
object PlaceRules {
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val INBOX = "inbox"
    const val CALENDAR = "calendar"
    const val HABITS = "habits"
    const val GOALS = "goals"
    const val PROJECTS = "projects"
    const val WANTS = "wants"
    const val TALLY = "tally"
    const val REVIEWS = "reviews"
    const val STATS = "stats"
    const val ARCHIVE = "archive"

    /** Every place, in the order the Places hub lists them. */
    val PLACES = listOf(TODAY, TOMORROW, INBOX, CALENDAR, HABITS, GOALS, PROJECTS, WANTS, TALLY, REVIEWS, STATS, ARCHIVE)

    private const val PHONE_LIMIT = 4

    /** How many pins a device holds, or null for no limit. */
    fun limit(device: DeviceKind): Int? = if (device == DeviceKind.PHONE) PHONE_LIMIT else null

    fun defaults(device: DeviceKind): List<String> = listOf(TODAY, TOMORROW, INBOX, PROJECTS)

    /** Adds [place] at the end, unless it is unknown, already pinned or would be one pin too many. */
    fun pin(pins: List<String>, place: String, device: DeviceKind): PinResult {
        val full = limit(device)?.let { pins.size >= it } ?: false
        if (place !in PLACES || place in pins || full) return PinResult(pins, refused = true)
        return PinResult(pins + place, refused = false)
    }

    /** Removes [place], unless it is not pinned or is the last pin. */
    fun unpin(pins: List<String>, place: String): PinResult {
        if (place !in pins || pins.size <= 1) return PinResult(pins, refused = true)
        return PinResult(pins - place, refused = false)
    }

    /** The pins read back from settings: known places once each, within the limit, else the defaults. */
    fun stored(stored: List<String>?, device: DeviceKind): List<String> {
        val known = stored.orEmpty().filter { it in PLACES }.distinct()
        val kept = limit(device)?.let { known.take(it) } ?: known
        return kept.ifEmpty { defaults(device) }
    }

    /** The number on the Places tab: what waits in the places that are not pinned. */
    fun count(pins: List<String>, waiting: Map<String, Int>): Int =
        waiting.filterKeys { it !in pins }.values.sum()
}
