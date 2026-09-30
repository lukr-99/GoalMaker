package com.goalmaker.app.ui.tally

import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyRule

/**
 * The Tally place (docs/tally.md): the filter, today's bar, the week's bars, the time per project,
 * a chip per category with time this week, and the owner's own categories and rules.
 */
data class TallyUiState(
    val loaded: Boolean = false,
    val filter: TallyFilter = TallyFilter(),
    val today: TallyBar? = null,
    val week: List<TallyBar> = emptyList(),
    /** The week's minutes by category, for the legend under its bars. */
    val weekSlices: List<TallySlice> = emptyList(),
    val projects: List<TallyProjectTime> = emptyList(),
    val chips: List<TallySlice> = emptyList(),
    /** Every category a rule can name: the owner's own first, then the shipped ones. */
    val categories: List<TallyCategory> = emptyList(),
    val own: List<TallyCategory> = emptyList(),
    val rules: List<TallyRule> = emptyList(),
) {
    /** The week's minutes under the filter. */
    val weekMinutes: Int get() = week.sumOf(TallyBar::minutes)

    /** A category's name, or null for one the owner has since removed. */
    fun nameOf(category: String): String? = categories.firstOrNull { it.id == category }?.name
}
