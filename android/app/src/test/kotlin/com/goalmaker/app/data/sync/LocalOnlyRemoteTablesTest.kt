package com.goalmaker.app.data.sync

import com.goalmaker.app.domain.sync.SyncedTable
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A dev build's server keeps what it is sent and has nothing to send back (docs/sign-in.md). */
class LocalOnlyRemoteTablesTest {
    private val remote = LocalOnlyRemoteTables { Instant.parse("2026-09-23T10:00:00Z") }

    @Test
    fun `a pushed row comes back as sent, stamped the way the server would`() = runTest {
        val row = JsonObject(mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive("Try the board")))

        val stored = remote.upsert("tasks", row)

        assertEquals(JsonPrimitive("t1"), stored["id"])
        assertEquals(JsonPrimitive("Try the board"), stored["title"])
        assertEquals(JsonPrimitive("2026-09-23T10:00:00Z"), stored[SyncedTable.UPDATED_AT])
    }

    @Test
    fun `nothing ever comes down`() = runTest {
        assertTrue(remote.pull("tasks", from = null, after = null, limit = 500).isEmpty())
    }
}
