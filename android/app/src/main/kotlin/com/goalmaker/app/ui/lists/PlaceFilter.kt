package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.TagList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update

/**
 * The area and tag filter one place keeps while the app runs (docs/lists.md): the lists share one,
 * and Projects, the calendar and the archive each have their own, so narrowing one never hides
 * anything in another. An area that was archived or deleted, or a tag that was deleted, falls away
 * instead of hiding everything. Disk work runs on [io].
 */
class PlaceFilter(areas: AreaList, tags: TagList, io: CoroutineDispatcher) {
    private val chosen = MutableStateFlow(ListFilter.NONE)

    /** The filter in force, what it can be set to, and the tag links it reads. */
    val choices: Flow<FilterChoices> = combine(
        chosen,
        areas.watch().flowOn(io),
        tags.watch().flowOn(io),
        tags.watchLinks().flowOn(io),
    ) { picked, areaList, tagList, links ->
        val active = areaList.filterNot { it.archived }
        FilterChoices(
            filter = ListFilter(
                areaId = picked.areaId?.takeIf { id -> active.any { it.id == id } },
                tagId = picked.tagId?.takeIf { id -> tagList.any { it.id == id } },
            ),
            areas = active,
            tags = tagList,
            links = links,
        )
    }

    /** Narrows the place to an area, or stops narrowing by area when [areaId] is null. */
    fun byArea(areaId: String?) = chosen.update { it.copy(areaId = areaId) }

    /** Narrows the place to a tag, or stops narrowing by tag when [tagId] is null. */
    fun byTag(tagId: String?) = chosen.update { it.copy(tagId = tagId) }
}
