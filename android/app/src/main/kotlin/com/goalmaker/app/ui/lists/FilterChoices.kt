package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.TagItem

/**
 * A place's area and tag filter as its chip row shows it: the [filter] in force, the [areas] and
 * [tags] to pick from (archived areas left out), and the [links] from each task to its tags that the
 * filter reads.
 */
data class FilterChoices(
    val filter: ListFilter = ListFilter.NONE,
    val areas: List<AreaItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val links: Map<String, Set<String>> = emptyMap(),
)
