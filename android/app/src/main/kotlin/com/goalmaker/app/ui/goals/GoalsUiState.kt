package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalPace
import java.time.LocalDate

/**
 * The Goals screen: the current periods' [sections] and next week's, the horizon the rings [filter] to,
 * the [picked] goal and its lit [chain], every goal for the parent pickers, and the ids of the shown
 * goals that are [hits] (a new one gets confetti).
 */
data class GoalsUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val sections: List<GoalSection> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val hits: Set<String> = emptySet(),
    val filter: GoalHorizon? = null,
    val picked: GoalRow? = null,
    val chain: Set<String> = emptySet(),
) {
    /** The dashboard: this year, month, week and today, one ring each. */
    val rings: List<GoalSection> get() = sections.filterNot(GoalSection::next)

    /** The ladder's rungs: every current period, or only the one the rings filter to. */
    val rungs: List<GoalSection> get() = rings.filter { filter == null || it.horizon == filter }

    /** Next week, for planning ahead, unless the rings filter to another horizon. */
    val nextWeek: GoalSection? get() = sections.firstOrNull(GoalSection::next)?.takeIf { filter == null || filter == GoalHorizon.WEEK }

    /** How many current goals need you: behind their period. */
    val behind: Int get() = rings.sumOf { section -> section.rows.count { it.standing.pace == GoalPace.BEHIND } }
}
