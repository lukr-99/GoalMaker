package com.goalmaker.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.settings.BoardView
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.ui.lists.PlaceFilter
import com.goalmaker.app.ui.lists.UndoEvent
import java.time.LocalDateTime
import java.time.ZoneId
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
 * who-made-it switch shows every item, only the owner's, or only Claude's. The area and tag filter
 * narrows the project list and the board the way it narrows the lists (docs/projects.md): an item
 * without an area of its own counts as being in its project's. A done item leaves the board
 * the project's number of days after the planning day it was finished, by [clock] and the owner's day
 * start, or when it is archived by hand. Finishing an item, archiving it and taking one out of the
 * project can be undone, as on the lists. How the board shows, as columns or a list, and which list
 * sections are folded away, stay on the device in [settings].
 */
class ProjectsViewModel(
    private val projects: ProjectList,
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val chosen = MutableStateFlow<String?>(null)
    private val madeBy = MutableStateFlow(ProjectRules.EVERYONE)
    private val filter = PlaceFilter(areas, tags, io)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)

    /** What the board offers to take back: an item moved to Done, archived, or taken out of the project. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    private val boards = combine(
        projects.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        chosen,
        combine(madeBy, filter.choices, ::Pair),
        settings.dayStartHour,
    ) { data, taskList, selectedId, (maker, choices), startHour ->
        val narrowed = choices.filter
        val items = taskList.filterNot { it.deleted }.groupBy { it.projectId }
        val listed = data.projects.filter { narrowed.keepsProject(it.areaId, items[it.id].orEmpty(), choices.links) }
        val selected = listed.firstOrNull { it.id == selectedId } ?: listed.firstOrNull()
        val today = PlanningDay.of(clock(), startHour)
        val (onBoard, offBoard) = if (selected == null) {
            emptyList<TaskItem>() to emptyList()
        } else {
            items[selected.id].orEmpty()
                .filter { ProjectRules.shows(maker, it.madeBy) && narrowed.matches(it, choices.links[it.id].orEmpty(), selected.areaId) }
                .partition { ProjectRules.onBoard(it, selected, today, zone(), startHour) }
        }
        ProjectsUiState(
            loaded = true,
            projects = listed,
            anyProject = data.projects.isNotEmpty(),
            selected = selected,
            board = if (selected == null) emptyList() else ProjectRules.board(onBoard),
            archived = offBoard.sortedWith(
                compareByDescending<TaskItem> { task -> task.completedAt?.let(SyncRules::instantOf) }.thenBy(TaskItem::id),
            ),
            madeBy = maker,
            filter = choices,
            milestones = selected?.let { data.milestonesOf(it.id) }.orEmpty(),
            openCounts = taskList.filterNot { it.deleted }
                .filter { it.projectId != null && it.boardColumn != ProjectRules.DONE }
                .groupingBy { it.projectId!! }
                .eachCount(),
        )
    }

    val uiState: StateFlow<ProjectsUiState> = combine(boards, settings.boardView, settings.collapsedColumns) { state, view, collapsed ->
        state.copy(view = view, collapsed = collapsed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    /** Which project the board shows. */
    fun select(id: String?) {
        chosen.value = id
    }

    /** Whose items the board shows: everyone's, the owner's, or Claude's ([ProjectRules.MAKER_FILTERS]). */
    fun showMadeBy(filter: String) {
        madeBy.value = filter
    }

    /** Narrows the projects and the board to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows the projects and the board to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

    /** Shows the board as columns behind tabs or as a list; this phone remembers it. */
    fun showView(view: BoardView) = settings.setBoardView(view)

    /** Folds a list section away, or opens it again; this phone remembers which are folded. */
    fun toggleColumn(column: String) {
        val collapsed = settings.collapsedColumns.value
        settings.setCollapsedColumns(if (column in collapsed) collapsed - column else collapsed + column)
    }

    /** Adds a project; [archiveAfterDays] is how long its done items stay on the board, null for until archived by hand. */
    fun addProject(draft: ProjectDraft, archiveAfterDays: Int? = ProjectRules.ARCHIVE_AFTER_DAYS) = write {
        projects.add(draft)?.let { project ->
            if (archiveAfterDays != project.archiveAfterDays) projects.setArchiveAfterDays(project.id, archiveAfterDays)
            chosen.value = project.id
        }
    }

    /** Changes a project to what [draft] says, and how long its done items stay on the board. */
    fun updateProject(id: String, draft: ProjectDraft, archiveAfterDays: Int?) = write {
        projects.update(id, draft)
        if (archiveAfterDays != projects.get(id)?.archiveAfterDays) projects.setArchiveAfterDays(id, archiveAfterDays)
    }

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

    /** Takes a done item off the board by hand; Undo puts it back. */
    fun archive(item: TaskItem) {
        write { tasks.setBoardArchived(item.id, true) }
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.ARCHIVED, item.title) { write { tasks.setBoardArchived(item.id, false) } })
    }

    /** Puts an item archived by hand back in Done. One that left with time comes back only by being reopened. */
    fun putBack(item: TaskItem) = write { tasks.setBoardArchived(item.id, false) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
