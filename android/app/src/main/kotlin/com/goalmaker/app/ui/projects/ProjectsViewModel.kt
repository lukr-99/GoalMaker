package com.goalmaker.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ArchiveRules
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
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
import java.util.Locale
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
 * sections are folded away, stay on the device in [settings]. A project may have a key, so its items
 * read GM-12 (docs/projects.md, "Item ids"): the form suggests one from a new project's name, a card
 * shows its id once the server has numbered it, its menu copies it, and the board's search finds an
 * item by its words, GM-12 or #12.
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
    private val search = MutableStateFlow("")
    private val copyEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** An item's id to put on the clipboard, with the usual confirmation. */
    val copies: SharedFlow<String> = copyEvents.asSharedFlow()

    /** What the board offers to take back: an item moved to Done, archived, or taken out of the project. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    private val boards = combine(
        projects.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        chosen,
        combine(madeBy, filter.choices, search, ::Triple),
        settings.dayStartHour,
    ) { data, taskList, selectedId, (maker, choices, query), startHour ->
        val narrowed = choices.filter
        val items = taskList.filterNot { it.deleted }.groupBy { it.projectId }
        val listed = data.projects.filter { narrowed.keepsProject(it.areaId, items[it.id].orEmpty(), choices.links) }
        val selected = listed.firstOrNull { it.id == selectedId } ?: listed.firstOrNull()
        val today = PlanningDay.of(clock(), startHour)
        val (onBoard, offBoard) = if (selected == null) {
            emptyList<TaskItem>() to emptyList()
        } else {
            val found = finder(query, selected)
            items[selected.id].orEmpty()
                .filter { ProjectRules.shows(maker, it.madeBy) && narrowed.matches(it, choices.links[it.id].orEmpty(), selected.areaId) }
                .filter(found)
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
            keys = data.projects.mapNotNull { project -> project.itemKey?.let { project.id to it } }.toMap(),
            query = query,
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

    /**
     * Adds a project; [archiveAfterDays] is how long its done items stay on the board, null for until
     * archived by hand, and [itemKey] what its items' ids start with (blank for none).
     */
    fun addProject(draft: ProjectDraft, archiveAfterDays: Int? = ProjectRules.ARCHIVE_AFTER_DAYS, itemKey: String = "") = write {
        projects.add(draft)?.let { project ->
            if (archiveAfterDays != project.archiveAfterDays) projects.setArchiveAfterDays(project.id, archiveAfterDays)
            if (itemKey.isNotBlank()) projects.setItemKey(project.id, itemKey)
            chosen.value = project.id
        }
    }

    /**
     * Changes a project to what [draft] says, how long its done items stay on the board, and, unless
     * [itemKey] is null, the key its items read by; the items keep their numbers.
     */
    fun updateProject(id: String, draft: ProjectDraft, archiveAfterDays: Int?, itemKey: String? = null) = write {
        projects.update(id, draft)
        if (archiveAfterDays != projects.get(id)?.archiveAfterDays) projects.setArchiveAfterDays(id, archiveAfterDays)
        if (itemKey != null && itemKey.trim().uppercase(Locale.ROOT) != projects.get(id)?.itemKey.orEmpty()) {
            projects.setItemKey(id, itemKey)
        }
    }

    /**
     * The key the form suggests for a project called [name]: one none of the other projects reads by
     * (docs/projects.md, "Item ids"), or null when the name has too little to make one from.
     */
    fun suggestKey(name: String, exceptId: String? = null): String? = ProjectRules.suggestItemKey(name, uiState.value.otherKeys(exceptId))

    /** Why [key] can't be the key of the project [exceptId] (null for a new one), or null when it can; blank is none. */
    fun keyProblem(key: String, exceptId: String? = null): KeyProblem? {
        val clean = key.trim().uppercase(Locale.ROOT)
        return when {
            clean.isEmpty() -> null
            !ProjectRules.isItemKey(clean) -> KeyProblem.NOT_VALID
            uiState.value.otherKeys(exceptId).any { it.equals(clean, ignoreCase = true) } -> KeyProblem.TAKEN
            else -> null
        }
    }

    /** What the board's search holds: words from an item's title or notes, or its id, GM-12 or #12. */
    fun setQuery(text: String) {
        search.value = text
    }

    /** Puts an item's id on the clipboard; an item the server hasn't numbered yet has none to copy. */
    fun copyId(item: TaskItem) {
        uiState.value.itemIdOf(item)?.let(copyEvents::tryEmit)
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
        // Undo goes back to the column the card was shown in, so a dropped item moved to Done is dropped again.
        val from = if (item.state == TaskState.DROPPED) ProjectRules.DROPPED else item.boardColumn ?: return
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
                    // Moving a dropped item to its stored column would reopen it, so it comes back dropped.
                    if (item.state != TaskState.DROPPED) item.boardColumn?.let { tasks.setBoardColumn(item.id, it) }
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

    // What the board's search keeps: an item by its id (GM-12, or #12 in the project on show), or by
    // every word of the query in its title or notes, ignoring case and accents.
    private fun finder(query: String, shown: ProjectItem): (TaskItem) -> Boolean {
        val wanted = ProjectRules.parseItemId(query)
        if (wanted != null) return { task -> ProjectRules.isItem(wanted, shown.itemKey, task.itemNumber, inProject = true) }
        val words = query.split(' ').map(ArchiveRules::fold).filter(String::isNotEmpty)
        return { task ->
            val text = ArchiveRules.fold(task.title) + "\n" + ArchiveRules.fold(task.notes)
            words.all { it in text }
        }
    }
}
