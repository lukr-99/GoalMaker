package com.goalmaker.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * A task on its way in from outside the app: a share from another app (spec, story 10) or the
 * quick-add widget. Whatever came in becomes a composer line, so every shortcut works on it, and
 * anything worth keeping behind it waits in the notes. With no day, area or project it lands in the
 * Inbox.
 */
class CaptureViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    auth: AuthGateway,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    val uiState: StateFlow<CaptureUiState> = combine(
        auth.session,
        areas.watch().flowOn(io),
        tags.watchNames().flowOn(io),
        projects.watch().flowOn(io).map { it.projects },
    ) { session, areaList, tagNames, projectList ->
        CaptureUiState(
            loaded = session != AuthSession.Loading,
            signedIn = session is AuthSession.SignedIn,
            areas = areaList,
            tagNames = tagNames,
            projects = projectList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    /** The planning day the preview calls "today". */
    fun today(): LocalDate = PlanningDay.of(clock(), dayStartHour.value)

    /** What the line says right now, the same reading the composer gives it. */
    fun preview(line: String): ComposerDraft = ComposerParser.parse(line, clock(), dayStartHour.value)

    /**
     * Saves the shared item with [notes] behind it, and waits for it: the sheet closes as soon as
     * this returns, so the write may not be left behind. False when there is nothing to save.
     */
    suspend fun save(draft: ComposerDraft, notes: String): Boolean {
        if (draft.command != null || draft.title.isBlank()) return false
        return withContext(io) { tasks.add(draft, notes) != null }
    }
}
