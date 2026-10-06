package com.goalmaker.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ArchiveRules
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.ui.lists.FilterChoices
import com.goalmaker.app.ui.lists.PlaceFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The archive of done tasks and its search (docs/archive.md), by words or by a project item's id
 * (GM-12, docs/projects.md "Item ids"), narrowed by the area and tag filter as the lists are, with the [projects] a done project item's chip names (docs/lists.md). Disk work runs
 * on [io].
 */
class ArchiveViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val filter = PlaceFilter(areas, tags, io)
    private val projectData = projects.watch().flowOn(io)

    /** What is typed in the search box. */
    val query: StateFlow<String> = search.asStateFlow()

    /** The area and tag filter, and what it can be set to. */
    val choices: StateFlow<FilterChoices> = filter.choices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterChoices())

    /** The done tasks that match the search and the filter, newest first; null until the replica has been read. */
    val results: StateFlow<List<TaskItem>?> = combine(tasks.watchAll().flowOn(io), search, filter.choices, projectData) { all, words, narrowing, data ->
        // An id, GM-12 or #12, finds that item; anything else is words to find.
        val found = ProjectRules.parseItemId(words)?.let { wanted ->
            ProjectRules.named(wanted, ArchiveRules.search(all, ""), data.projects.associateBy(ProjectItem::id))
        } ?: ArchiveRules.search(all, words)
        narrowing.filter.apply(found, narrowing.links, data.projects.associate { it.id to it.areaId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The projects that are still there, by id, for the chips. */
    val projects: StateFlow<Map<String, ProjectItem>> = projectData
        .map { data -> data.projects.associateBy(ProjectItem::id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(text: String) {
        search.value = text
    }

    /** Narrows the archive to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows the archive to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

    /** Opens the task again, on its planned day. */
    fun reopen(id: String) {
        viewModelScope.launch(io) { tasks.setDone(id, false) }
    }
}
