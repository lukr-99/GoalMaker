package com.goalmaker.app.ui.activity

import com.goalmaker.app.application.activity.ActivityEntry
import com.goalmaker.app.application.activity.ActivityLog
import com.goalmaker.app.application.activity.UndoOutcome
import com.goalmaker.app.application.sync.RemoteUnavailableException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Activity screen's list and Undo over a fake log (docs/activity.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {
    private class FakeLog : ActivityLog {
        var entries = listOf<ActivityEntry>()
        var outcome = UndoOutcome.UNDONE
        var offline = false
        val undone = mutableListOf<Long>()

        override suspend fun recent(limit: Int): List<ActivityEntry> {
            if (offline) throw RemoteUnavailableException("offline")
            return entries
        }

        override suspend fun undo(entryId: Long): UndoOutcome {
            undone += entryId
            if (outcome == UndoOutcome.UNDONE) {
                entries = entries.map { if (it.id == entryId) it.copy(undoneAt = Instant.parse("2026-09-19T10:00:00Z")) else it }
            }
            return outcome
        }
    }

    private val log = FakeLog()
    private var syncs = 0
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(id: Long, entity: String, entityId: String, action: String, title: String, actor: String = "owner") =
        ActivityEntry(
            id = id,
            entity = entity,
            entityId = entityId,
            action = action,
            actor = actor,
            before = if (action == "create") null else JsonObject(mapOf("title" to JsonPrimitive(title), "status" to JsonPrimitive("open"))),
            after = JsonObject(mapOf("title" to JsonPrimitive(title), "status" to JsonPrimitive(if (action == "update") "done" else "open"))),
            createdAt = Instant.parse("2026-09-19T09:00:00Z").plusSeconds(id),
            undoneAt = null,
        )

    @Test
    fun `undo is offered on each row's latest change only`() {
        log.entries = listOf(
            entry(1, "tasks", "a", "create", "Call the bank", actor = "claude"),
            entry(2, "tasks", "a", "update", "Call the bank"),
            entry(3, "tasks", "b", "create", "Buy milk"),
        )

        val viewModel = ActivityViewModel(log, { syncs++ }, dispatcher)

        val rows = viewModel.uiState.value.rows
        assertEquals(listOf(3L, 2L, 1L), rows.map { it.entry.id })
        assertEquals(listOf(true, true, false), rows.map { it.undoable })
        assertEquals("completed", rows[1].change.change)
    }

    @Test
    fun `an undo is reported, synced and the list read again`() {
        log.entries = listOf(entry(1, "tasks", "a", "create", "Oops", actor = "claude"))
        val viewModel = ActivityViewModel(log, { syncs++ }, dispatcher)

        viewModel.undo(viewModel.uiState.value.rows.single())

        assertEquals(listOf(1L), log.undone)
        assertEquals(UndoOutcome.UNDONE, viewModel.uiState.value.undone)
        assertEquals(1, syncs)
        assertEquals(false, viewModel.uiState.value.rows.single().undoable)
    }

    @Test
    fun `a change the row moved on from is explained and nothing syncs`() {
        log.entries = listOf(entry(1, "tasks", "a", "update", "Call the bank"))
        log.outcome = UndoOutcome.CHANGED_SINCE
        val viewModel = ActivityViewModel(log, { syncs++ }, dispatcher)

        viewModel.undo(viewModel.uiState.value.rows.single())

        assertEquals(UndoOutcome.CHANGED_SINCE, viewModel.uiState.value.undone)
        assertEquals(0, syncs)
    }

    @Test
    fun `offline, the screen says it needs a connection`() {
        log.offline = true

        val viewModel = ActivityViewModel(log, { syncs++ }, dispatcher)

        assertTrue(viewModel.uiState.value.unavailable)
        assertTrue(viewModel.uiState.value.loaded)
    }
}
