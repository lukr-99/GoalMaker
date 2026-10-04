package com.goalmaker.app.ui.lifegoals

import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.application.planning.LifeGoalPicture
import com.goalmaker.app.application.planning.TimeLeft

/** A life goal as the Life goals place draws it: how far its by date is, and its pictures in order. */
data class LifeGoalRow(
    val goal: LifeGoalItem,
    val timeLeft: TimeLeft?,
    val pictures: List<LifeGoalPicture>,
)
