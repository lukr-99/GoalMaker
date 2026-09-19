package com.goalmaker.app.ui.goals

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The goals for this year, month, week and day, and the cascade (docs/goals.md). */
@Serializable
data object GoalsKey : NavKey
