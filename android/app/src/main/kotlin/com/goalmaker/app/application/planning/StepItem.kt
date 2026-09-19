package com.goalmaker.app.application.planning

/** One line of a task's checklist (spec, story 14). Steps are not tasks. */
data class StepItem(val id: String, val taskId: String, val title: String, val done: Boolean)
