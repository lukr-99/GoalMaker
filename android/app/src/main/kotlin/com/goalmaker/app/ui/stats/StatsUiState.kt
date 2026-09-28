package com.goalmaker.app.ui.stats

import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantStats

import com.goalmaker.app.application.planning.StatsDigest

/** What the stats screen draws once the lists have loaded (docs/stats.md). */
data class StatsUiState(
    val loaded: Boolean = false,
    val digest: StatsDigest = StatsDigest(),
    /** Bought, dropped and not spent; null until a want has been decided (docs/wants.md). */
    val wants: WantStats? = null,
    val currency: String = WantCooldowns.DEFAULT.currency,
)
