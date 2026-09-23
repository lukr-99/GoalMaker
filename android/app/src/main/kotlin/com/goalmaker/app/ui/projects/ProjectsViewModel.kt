package com.goalmaker.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.composer.ComposerDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Projects screen (docs/projects.md, spec stories 43 to 50): the owner's projects and the board
 * of the one being looked at. An item is a task, so moving it around the board writes to [tasks]. The
 * who-made-it switch shows every item, only the owner's, or only Claude's.
 */
class ProjectsViewModel(
    private val projects: ProjectList,
    private val tasks: TaskList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val chosen = MutableStateFlow<String?>(null)
    private val madeBy = MutableStateFlow(ProjectRules.EVERYONE)

    val uiState: StateFlow<ProjectsUiState> = combine(
        projects.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        chosen,
        madeBy,
    ) { data, taskList, selectedId, filter ->
        val selected = data.find(selectedId) ?: data.projects.firstOrNull()
        ProjectsUiState(
            loaded = true,
            projects = data.projects,
            selected = selected,
            board = if (selected == null) {
                emptyList()
            } else {
                ProjectRules.board(taskList.filter { it.projectId == selected.id && ProjectRules.shows(filter, it.madeBy) })
            },
            madeBy = filter,
            milestones = selected?.let { data.milestonesOf(it.id) }.orEmpty(),
            openCounts = taskList.filterNot { it.deleted }
                .filter { it.projectId != null && it.boardColumn != ProjectRules.DONE }
                .groupingBy { it.projectId!! }
                .eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    /** Which project the board shows. */
    fun select(id: String?) {
        chosen.value = id
    }

    /** Whose items the board shows: everyone's, the owner's, or Claude's ([ProjectRules.MAKER_FILTERS]). */
    fun showMadeBy(filter: String) {
        madeBy.value = filter
    }

    fun addProject(draft: ProjectDraft) = write { projects.add(draft)?.let { chosen.value = it.id } }

    fun updateProject(id: String, draft: ProjectDraft) = write { projects.update(id, draft) }

    fun setStatus(id: String, status: String) = write { projects.setStatus(id, status) }

    fun deleteProject(id: String) = write {
        projects.delete(id)
        chosen.value = null
    }

    fun addMilestone(projectId: String, name: String) = write { projects.addMilestone(projectId, name) }

    fun deleteMilestone(id: String) = write { projects.deleteMilestone(id) }

    /** Adds an item to the project on show, in the column and at the priority chosen for it, with its notes. */
    fun addItem(title: String, itemType: String, column: String, priority: String, notes: String) {
        val projectId = uiState.value.selected?.id ?: return
        write {
            tasks.add(ComposerDraft(title = title), notes)?.let { task ->
                tasks.setProject(task.id, projectId, itemType)
                tasks.setBoardColumn(task.id, column)
                tasks.setPriority(task.id, priority)
            }
        }
    }

    fun move(id: String, column: String) = write { tasks.setBoardColumn(id, column) }

    fun setPriority(id: String, priority: String) = write { tasks.setPriority(id, priority) }

    fun setItemType(id: String, itemType: String) = write { tasks.setItemType(id, itemType) }

    fun setMilestone(id: String, milestoneId: String?) = write { tasks.setMilestone(id, milestoneId) }

    /** Takes an item out of its project; it stays as a plain task. */
    fun removeFromProject(id: String) = write { tasks.setProject(id, null) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
