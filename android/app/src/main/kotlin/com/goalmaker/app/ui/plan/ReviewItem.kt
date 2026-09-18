package com.goalmaker.app.ui.plan

import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.TaskItem

/** A task step 1 asks about, with what the ritual shows for it now. */
data class ReviewItem(val task: TaskItem, val decision: PlanDecision)
