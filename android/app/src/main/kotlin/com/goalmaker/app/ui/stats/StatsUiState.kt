package com.goalmaker.app.ui.stats

import com.goalmaker.app.application.planning.StatsDigest

/** What the stats screen draws once the lists have loaded (docs/stats.md). */
data class StatsUiState(
    val loaded: Boolean = false,
    val digest: StatsDigest = StatsDigest(),
)
