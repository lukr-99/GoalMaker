package com.goalmaker.app.ui.places

import com.goalmaker.app.domain.navigation.PlaceRules

/** What each tile on the Places hub shows (ADR 0014), worked out by [PlacesBoard]. */
data class PlacesDigest(
    val todayDone: Int = 0,
    val todayTotal: Int = 0,
    val tomorrow: Int = 0,
    val inbox: Int = 0,
    val comingWeek: Int = 0,
    val habitsMet: Int = 0,
    val habitsDue: Int = 0,
    val goalsHit: Int = 0,
    val goalsTotal: Int = 0,
    val projectsOpen: Int = 0,
    val projectsDoing: Int = 0,
    val letterWaiting: Boolean = false,
    val doneThisWeek: Int = 0,
    val archived: Int = 0,
) {
    /** What waits in each place, which the Places tab counts for the places that are not pinned. */
    val waiting: Map<String, Int>
        get() = buildMap {
            if (inbox > 0) put(PlaceRules.INBOX, inbox)
            if (letterWaiting) put(PlaceRules.REVIEWS, 1)
        }
}
