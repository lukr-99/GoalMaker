package com.goalmaker.app.ui.share

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
 * What another app shared, on its way into a task (spec, story 10). The shared text becomes a
 * composer line, so every shortcut works on it, and the link it came from waits in the notes. With
 * no day, area or project the task lands in the Inbox.
 */
class ShareViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    auth: AuthGateway,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    val uiState: StateFlow<ShareUiState> = combine(
        auth.session,
        areas.watch().flowOn(io),
        tags.watchNames().flowOn(io),
        projects.watch().flowOn(io).map { it.projects },
    ) { session, areaList, tagNames, projectList ->
        ShareUiState(
            loaded = session != AuthSession.Loading,
            signedIn = session is AuthSession.SignedIn,
            areas = areaList,
            tagNames = tagNames,
            projects = projectList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState())

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
