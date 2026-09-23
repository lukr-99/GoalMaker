package com.goalmaker.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.ui.lists.UndoEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Projects screen (docs/projects.md, spec stories 43 to 50): the owner's projects and the board
 * of the one being looked at. An item is a task, so moving it around the board writes to [tasks]. The
 * who-made-it switch shows every item, only the owner's, or only Claude's. Finishing an item and taking
 * one out of the project can be undone, as on the lists.
 */
class ProjectsViewModel(
    private val projects: ProjectList,
    private val tasks: TaskList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val chosen = MutableStateFlow<String?>(null)
    private val madeBy = MutableStateFlow(ProjectRules.EVERYONE)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)

    /** What the board offers to take back: an item moved to Done, or one taken out of the project. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

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

    /** Moves an item to [column]; moving it to Done can be undone, back to the column it came from. */
    fun move(item: TaskItem, column: String) {
        write { tasks.setBoardColumn(item.id, column) }
        val from = item.boardColumn ?: return
        if (column == ProjectRules.DONE) {
            undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.DONE, item.title) { write { tasks.setBoardColumn(item.id, from) } })
        }
    }

    fun setPriority(id: String, priority: String) = write { tasks.setPriority(id, priority) }

    fun setItemType(id: String, itemType: String) = write { tasks.setItemType(id, itemType) }

    fun setMilestone(id: String, milestoneId: String?) = write { tasks.setMilestone(id, milestoneId) }

    /** Takes an item out of its project; it stays as a plain task. Undo puts it back where it was. */
    fun removeFromProject(item: TaskItem) {
        val projectId = item.projectId ?: return
        write { tasks.setProject(item.id, null) }
        undoEvents.tryEmit(
            UndoEvent(UndoEvent.Kind.OUT_OF_PROJECT, item.title) {
                write {
                    tasks.setProject(item.id, projectId, item.itemType)
                    item.boardColumn?.let { tasks.setBoardColumn(item.id, it) }
                    tasks.setMilestone(item.id, item.milestoneId)
                }
            },
        )
    }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
