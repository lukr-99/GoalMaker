package com.goalmaker.app.application.planning

/** One column of a project's board with the items in it, in the order the board shows them. */
data class ProjectColumn(val column: String, val items: List<TaskItem> = emptyList())
