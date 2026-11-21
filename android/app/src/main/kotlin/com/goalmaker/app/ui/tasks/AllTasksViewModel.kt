package com.goalmaker.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.planning.AllTasksRules
import com.goalmaker.app.ui.lists.PlaceFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The All tasks place (docs/all-tasks.md): every open task, grouped by area, searched as you type and
 * narrowed by an area and tag filter of its own, so a task filed into an area without a day can always
 * be found. Project items show only when asked for. Disk work runs on [io].
 */
class AllTasksViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val withProjectItems = MutableStateFlow(false)
    private val filter = PlaceFilter(areas, tags, io)

    val uiState = combine(
        combine(tasks.watchAll().flowOn(io), areas.watch().flowOn(io), ::Pair),
        filter.choices,
        projects.watch().flowOn(io),
        search,
        withProjectItems,
    ) { (all, allAreas), choices, data, query, projectItems ->
        val byProject = data.projects.associate { it.id to it.areaId }
        val narrowed = choices.filter.apply(all, choices.links, byProject)
        AllTasksUiState(
            groups = AllTasksRules.groups(narrowed, allAreas.map { it.id }, byProject, query, projectItems),
            areas = allAreas.associateBy { it.id },
            projects = data.projects.associateBy(ProjectItem::id),
            choices = choices,
            query = query,
            projectItems = projectItems,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTasksUiState())

    fun setQuery(text: String) {
        search.value = text
    }

    /** Shows project items too, or hides them again. */
    fun showProjectItems(show: Boolean) {
        withProjectItems.value = show
    }

    /** Narrows the place to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows the place to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

    fun complete(id: String) {
        viewModelScope.launch(io) { tasks.setDone(id, true) }
    }
}
