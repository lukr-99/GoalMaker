package com.goalmaker.app.application.planning

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Life goals (docs/life-goals.md), pinned by contracts/vectors/life-goals.json, which the Windows app
 * and the connector run too: how far a by date is, and the order of the place.
 */
object LifeGoalRules {
    const val OPEN = "open"
    const val ACHIEVED = "achieved"
    const val DROPPED = "dropped"

    /** The years the editor offers for the by date. */
    val BY_YEARS = listOf(5, 10, 20)

    /** How far [by] is from the planning day [today]; null without a by date. */
    fun timeLeft(by: LocalDate?, today: LocalDate): TimeLeft? {
        if (by == null) return null
        if (by.isBefore(today)) return TimeLeft(TimeLeftUnit.PAST, 0)
        if (by == today) return TimeLeft(TimeLeftUnit.TODAY, 0)
        var months = (by.year * 12 + by.monthValue) - (today.year * 12 + today.monthValue)
        if (by.dayOfMonth < today.dayOfMonth) months -= 1
        return when {
            months >= 12 -> TimeLeft(TimeLeftUnit.YEARS, months / 12)
            months >= 1 -> TimeLeft(TimeLeftUnit.MONTHS, months)
            else -> TimeLeft(TimeLeftUnit.DAYS, ChronoUnit.DAYS.between(today, by).toInt())
        }
    }

    /** Open ones in the owner's order, then achieved and dropped ones, the most recently closed first. */
    fun ordered(goals: List<LifeGoalItem>): List<LifeGoalItem> {
        val open = goals.filter { it.status == OPEN }.sortedWith(
            compareBy<LifeGoalItem> { it.position }.thenBy { instant(it.createdAt) }.thenBy { it.id },
        )
        val closed = goals.filter { it.status != OPEN }.sortedWith(
            compareByDescending<LifeGoalItem> { instant(it.closedAt) }.thenBy { it.id },
        )
        return open + closed
    }

    /** The open life goals in the owner's order, the ones the why reminder and the widget go through. */
    fun open(goals: List<LifeGoalItem>): List<LifeGoalItem> =
        ordered(goals.filter { !it.deleted && it.status == OPEN })

    private fun instant(text: String?): Instant =
        if (text.isNullOrEmpty()) Instant.EPOCH else Instant.parse(text)
}
