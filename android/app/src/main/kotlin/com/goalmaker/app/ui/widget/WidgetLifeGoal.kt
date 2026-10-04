package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.TimeLeft

/**
 * One slide of the Life goals widget (docs/life-goals.md): a picture of an open life goal, or the
 * life goal itself when it has no pictures, with its title, why and time left.
 */
data class WidgetLifeGoal(
    val lifeGoalId: String,
    val title: String,
    val why: String,
    val timeLeft: TimeLeft?,
    val pictureId: String? = null,
)
