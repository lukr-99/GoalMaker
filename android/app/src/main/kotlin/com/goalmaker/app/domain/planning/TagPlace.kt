package com.goalmaker.app.domain.planning

/** Where a tag belongs: one area or one project (docs/lists.md, "Tags"). */
sealed interface TagPlace {
    val id: String

    data class Area(override val id: String) : TagPlace

    data class Project(override val id: String) : TagPlace
}
