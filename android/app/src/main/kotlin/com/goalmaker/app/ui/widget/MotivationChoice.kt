package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.GoalHorizon

/**
 * How one Motivation widget is set up in its configure screen: the owner's own [text], or the goals
 * of one [horizon]. Both are kept, so switching back and forth loses nothing.
 */
data class MotivationChoice(
    val mode: MotivationMode = MotivationMode.GOALS,
    val text: String = "",
    val horizon: GoalHorizon = GoalHorizon.WEEK,
) {
    /** True when there is something to show: words written, or goals picked. */
    val ready: Boolean get() = mode == MotivationMode.GOALS || text.isNotBlank()

    companion object {
        /** The horizons a Motivation widget can show; a day's goals change too fast to motivate. */
        val HORIZONS = listOf(GoalHorizon.WEEK, GoalHorizon.MONTH, GoalHorizon.YEAR)

        /** The longest text kept, so a pasted essay can't swell the widget's storage. */
        const val MAX_TEXT = 400
    }
}
