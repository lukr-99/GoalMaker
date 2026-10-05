package com.goalmaker.app.ui.tally

import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules

/**
 * The Tally place (docs/tally.md): the filter, the day's bar (today, or the day picked in the week),
 * the week's bars, the time per project, a chip per category with time this week, the owner's own
 * categories and rules, and this phone's own look at its time: the day by hour and the apps.
 */
data class TallyUiState(
    val loaded: Boolean = false,
    val filter: TallyFilter = TallyFilter(),
    /** The day the day panel shows: today, unless a day of the week is picked. */
    val day: TallyBar? = null,
    val isToday: Boolean = true,
    val week: List<TallyBar> = emptyList(),
    /** The week's minutes by category, for the legend under its bars. */
    val weekSlices: List<TallySlice> = emptyList(),
    val projects: List<TallyProjectTime> = emptyList(),
    val chips: List<TallySlice> = emptyList(),
    /** Every category a rule can name: the owner's own first, then the shipped ones. */
    val categories: List<TallyCategory> = emptyList(),
    val own: List<TallyCategory> = emptyList(),
    val rules: List<TallyRule> = emptyList(),
    /** Whether this phone counts (on, with usage access), so its hours and apps can show. */
    val counting: Boolean = false,
    /** The shown day's 24 hours on this phone, from the hour the planning day starts. */
    val hours: List<TallyHourBar> = emptyList(),
    val appScope: TallyAppScope = TallyAppScope.DAY,
    /** This phone's apps by category, for the shown day or the week. */
    val apps: List<TallyAppGroup> = emptyList(),
    /** The last 8 weeks under the filter, oldest first. */
    val trend: List<TallyBar> = emptyList(),
    /** This week on the phone and on the PC, by category. */
    val devices: List<TallyDeviceTime> = emptyList(),
) {
    /** The apps that landed in Other, most first: what is left to sort. */
    val toSort: List<TallyAppRow> get() = apps.firstOrNull { it.category == TallyRules.OTHER }?.apps.orEmpty()

    /** The week's minutes under the filter. */
    val weekMinutes: Int get() = week.sumOf(TallyBar::minutes)

    /** Where the day picked sits in the week's bars. */
    val dayIndex: Int get() = week.indexOfFirst { it.day == day?.day }

    /** The PC's apps stay on the PC, so with the PC chosen this phone has none to show. */
    val otherDevice: Boolean get() = filter.kind == TallyRules.PC

    /** A category's name, or null for one the owner has since removed. */
    fun nameOf(category: String): String? = categories.firstOrNull { it.id == category }?.name
}
