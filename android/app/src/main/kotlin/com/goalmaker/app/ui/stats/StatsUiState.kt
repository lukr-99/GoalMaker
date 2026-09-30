package com.goalmaker.app.ui.stats

import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantStats

import com.goalmaker.app.application.planning.StatsDigest
import com.goalmaker.app.ui.tally.TallyBar
import com.goalmaker.app.ui.tally.TallySlice

/** What the stats screen draws once the lists have loaded (docs/stats.md). */
data class StatsUiState(
    val loaded: Boolean = false,
    val digest: StatsDigest = StatsDigest(),
    /** Bought, dropped and not spent; null until a want has been decided (docs/wants.md). */
    val wants: WantStats? = null,
    val currency: String = WantCooldowns.DEFAULT.currency,
    /** Tally's twelve weeks, oldest first; empty while none of them has time (docs/tally.md). */
    val tallyWeeks: List<TallyBar> = emptyList(),
    /** The twelve weeks' minutes by category, most first. */
    val tallySlices: List<TallySlice> = emptyList(),
)
