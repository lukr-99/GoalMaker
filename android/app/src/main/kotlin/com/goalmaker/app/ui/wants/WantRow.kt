package com.goalmaker.app.ui.wants

import com.goalmaker.app.application.planning.WantItem
import com.goalmaker.app.application.planning.WantState

/** A want as the Wants place draws it: where it stands today, how far its ring has run, days left. */
data class WantRow(
    val want: WantItem,
    val state: WantState,
    val progress: Double,
    val daysLeft: Int,
)
