package com.goalmaker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.activity.ActivityEntry
import com.goalmaker.app.application.activity.ActivityLog
import com.goalmaker.app.application.activity.UndoOutcome
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The activity log with undo (docs/activity.md), read online. Undo is offered on each row's latest
 * change, since the server refuses to undo a change the row has moved on from; undoing it again
 * (the undo is a change too) is how a mistaken undo is taken back. [requestSync] brings the restored
 * row into the replica right away.
 */
class ActivityViewModel(
    private val log: ActivityLog,
    private val requestSync: () -> Unit,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = call { load() }

    fun undo(row: ActivityRow) = call {
        val outcome = withContext(io) { log.undo(row.entry.id) }
        state.update { it.copy(undone = outcome) }
        if (outcome == UndoOutcome.UNDONE) requestSync()
        load()
    }

    /** The owner saw what the last Undo came to. */
    fun messageShown() = state.update { it.copy(undone = null) }

    private suspend fun load() {
        val entries = withContext(io) { log.recent() }
        state.update { it.copy(loaded = true, rows = rows(entries), unavailable = false) }
    }

    private fun call(work: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                work()
            } catch (_: RemoteUnavailableException) {
                state.update { it.copy(loaded = true, unavailable = true) }
            } catch (_: RemoteRejectedException) {
                state.update { it.copy(loaded = true, unavailable = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        // Rows the server can put back (undo_activity in supabase/migrations/0008).
        private val UNDOABLE = setOf("areas", "tags", "goals", "goal_entries", "tasks", "task_steps", "task_tags", "reminders", "ritual_runs", "reviews")

        /** The entries as rows, newest first, with Undo on each row's latest change that stands. */
        fun rows(entries: List<ActivityEntry>): List<ActivityRow> {
            val seen = mutableSetOf<Pair<String, String>>()
            return entries.sortedByDescending(ActivityEntry::id).map { entry ->
                val latest = seen.add(entry.entity to entry.entityId)
                ActivityRow(entry, entry.change, latest && entry.undoneAt == null && entry.entity in UNDOABLE)
            }
        }
    }
}
