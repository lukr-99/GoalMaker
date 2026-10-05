package com.goalmaker.app.ui.wants

import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.WantState

/** The Wants place (docs/wants.md): the rows of the chosen filter, how many each filter holds, the thresholds. */
data class WantsUiState(
    val loaded: Boolean = false,
    val filter: WantState = WantState.READY,
    val rows: List<WantRow> = emptyList(),
    val counts: Map<WantState, Int> = emptyMap(),
    val cooldowns: WantCooldowns = WantCooldowns.DEFAULT,
    /** The tab on screen: [WantRules.WANT] or [WantRules.NEED]. */
    val kind: String = WantRules.WANT,
    /** The open needs in their order, and the bought and dropped ones, newest first. */
    val needs: List<NeedRow> = emptyList(),
    val closedNeeds: List<NeedRow> = emptyList(),
) {
    val empty: Boolean get() = counts.values.sum() == 0
}
