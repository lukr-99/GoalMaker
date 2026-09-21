package com.goalmaker.app.application.problems

import com.goalmaker.app.domain.problems.Problem
import com.goalmaker.app.domain.problems.ProblemRules
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The problems waiting for the owner in Settings (docs/problems.md). It lives for as long as the app
 * runs: a problem that has not come right by the next start will report itself again, and one that has
 * should never have interrupted anything in the first place.
 */
class ProblemLog(private val clock: Clock = Clock.systemUTC()) {
    private val state = MutableStateFlow<List<Problem>>(emptyList())

    /** What is wrong now, newest first. */
    val problems: StateFlow<List<Problem>> = state.asStateFlow()

    /** That kind went wrong; [detail] is kept behind "what happened". */
    fun report(kind: String, detail: String?) =
        state.update { ProblemRules.report(it, kind, clock.instant(), detail) }

    /** That kind came right by itself. */
    fun clear(kind: String) = state.update { ProblemRules.clear(it, kind) }

    /** The owner has seen them. */
    fun read() = state.update { ProblemRules.read(it) }
}
