package com.goalmaker.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ArchiveRules
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
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
 * The archive of done tasks and its search (docs/archive.md), with the [projects] a done project
 * item's chip names (docs/lists.md). Disk work runs on [io].
 */
class ArchiveViewModel(
    private val tasks: TaskList,
    projects: ProjectList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val search = MutableStateFlow("")

    /** What is typed in the search box. */
    val query: StateFlow<String> = search.asStateFlow()

    /** The done tasks that match, newest first; null until the replica has been read. */
    val results: StateFlow<List<TaskItem>?> = combine(tasks.watchAll().flowOn(io), search) { all, words ->
        ArchiveRules.search(all, words)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The projects that are still there, by id, for the chips. */
    val projects: StateFlow<Map<String, ProjectItem>> = projects.watch().flowOn(io)
        .map { data -> data.projects.associateBy(ProjectItem::id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(text: String) {
        search.value = text
    }

    /** Opens the task again, on its planned day. */
    fun reopen(id: String) {
        viewModelScope.launch(io) { tasks.setDone(id, false) }
    }
}
