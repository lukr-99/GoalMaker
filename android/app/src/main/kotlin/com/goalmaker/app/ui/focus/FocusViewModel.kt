package com.goalmaker.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.FocusBoardRules
import com.goalmaker.app.ui.lists.PlaceFilter
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Focus Board (docs/focus.md): the board in the owner's order, narrowed by an area and tag filter of
 * its own, and the add sheet's tasks for what is typed in its search box. A task can also be made
 * straight onto the board from a typed line, which reads the composer's shortcuts (docs/composer.md).
 * Disk work runs on [io].
 */
class FocusViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    private val showAddSheet = MutableStateFlow(false)
    private val search = MutableStateFlow("")
    private val filter = PlaceFilter(areas, tags, projects, io)

    val uiState = combine(
        combine(tasks.watchAll().flowOn(io), areas.watch().flowOn(io), ::Pair),
        filter.choices,
        projects.watch().flowOn(io),
        search,
        showAddSheet,
    ) { (all, allAreas), choices, data, query, addSheet ->
        val byProject = data.projects.associate { it.id to it.areaId }
        val board = FocusBoardRules.board(all)
        FocusUiState(
            items = choices.filter.apply(board, choices.links, byProject),
            onBoard = board.size,
            areas = allAreas.filterNot(AreaItem::archived),
            choices = choices,
            query = query,
            candidates = choices.filter.apply(FocusBoardRules.candidates(all, query), choices.links, byProject),
            showAddSheet = addSheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun complete(id: String) {
        viewModelScope.launch(io) { tasks.setDone(id, true) }
    }

    fun removeFromBoard(id: String) {
        viewModelScope.launch(io) { tasks.setFocusPosition(id, null) }
    }

    /**
     * Moves the task at [fromIndex] to just before the item at [toIndex] in the board as shown. Called
     * after a drag gesture settles. While a filter hides part of the board, the task lands next to its
     * shown neighbours, so the hidden tasks keep their places.
     */
    fun move(fromIndex: Int, toIndex: Int) {
        val shown = uiState.value.items
        if (fromIndex == toIndex || fromIndex !in shown.indices) return
        val task = shown[fromIndex]
        val rest = shown.toMutableList().apply { removeAt(fromIndex) }
        val insertAt = if (toIndex > fromIndex) toIndex - 1 else toIndex
        val before = rest.getOrNull(insertAt - 1)
        val after = rest.getOrNull(insertAt)
        viewModelScope.launch(io) { tasks.setFocusPosition(task.id, FocusBoardRules.between(before, after)) }
    }

    fun addToBoard(task: TaskItem) {
        viewModelScope.launch(io) { tasks.setFocusPosition(task.id, FocusBoardRules.appendPosition(tasks.all())) }
        dismissAddSheet()
    }

    /**
     * Makes a task from [line] and puts it at the end of the board: the composer's shortcuts work, so
     * "@School #forms friday" sets the area, a tag and the day. False when there is nothing to save,
     * so the field keeps its text.
     */
    fun create(line: String): Boolean {
        val draft = ComposerParser.parse(line, clock(), settings.dayStartHour.value)
        if (draft.command != null || draft.title.isBlank()) return false
        viewModelScope.launch(io) {
            val position = FocusBoardRules.appendPosition(tasks.all())
            tasks.add(draft)?.let { tasks.setFocusPosition(it.id, position) }
        }
        dismissAddSheet()
        return true
    }

    /** What is typed in the add sheet's search box. */
    fun setQuery(text: String) {
        search.value = text
    }

    /** Narrows the board and the add sheet to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows the board and the add sheet to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

    fun openAddSheet() {
        showAddSheet.update { true }
    }

    fun dismissAddSheet() {
        showAddSheet.update { false }
        search.value = ""
    }
}
