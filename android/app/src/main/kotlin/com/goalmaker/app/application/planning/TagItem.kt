package com.goalmaker.app.application.planning

/**
 * A tag as the manager and the filters show it. It belongs to one area ([areaId]) or one project
 * ([projectId]), or with neither it is for everything (docs/lists.md, "Tags").
 */
data class TagItem(val id: String, val name: String, val areaId: String? = null, val projectId: String? = null)
