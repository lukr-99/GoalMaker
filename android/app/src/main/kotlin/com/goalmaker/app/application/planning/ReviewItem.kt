package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A review of one period (docs/reviews.md): the owner's [mood] and [energy] from 1 to 5, the
 * [summary] a Claude routine may write, and the prompts it asked with their answers.
 */
data class ReviewItem(
    val id: String,
    val kind: String,
    val periodStart: LocalDate,
    val mood: Int? = null,
    val energy: Int? = null,
    val summary: String = "",
    val reflections: List<Reflection> = emptyList(),
    val deleted: Boolean = false,
) {
    /** Whether anything was written: it is worth keeping and worth showing in the list of past reviews. */
    val written: Boolean get() = mood != null || energy != null || summary.isNotBlank() || reflections.any { it.answer.isNotBlank() }
}
