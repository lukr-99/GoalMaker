package com.goalmaker.app.ui.wants

import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantState

/** The Wants place (docs/wants.md): the rows of the chosen filter, how many each filter holds, the thresholds. */
data class WantsUiState(
    val loaded: Boolean = false,
    val filter: WantState = WantState.READY,
    val rows: List<WantRow> = emptyList(),
    val counts: Map<WantState, Int> = emptyMap(),
    val cooldowns: WantCooldowns = WantCooldowns.DEFAULT,
) {
    val empty: Boolean get() = counts.values.sum() == 0
}
