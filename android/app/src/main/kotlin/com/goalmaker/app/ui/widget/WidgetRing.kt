package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.GoalHorizon

/**
 * One ring on the Goals widget: a horizon's current period, how much of its goals is done
 * ([fraction], 0 to 1, the mean the Goals screen's ring shows) and how many of its [total] are [hits].
 */
data class WidgetRing(
    val horizon: GoalHorizon,
    val fraction: Double,
    val hits: Int,
    val total: Int,
)
