package com.goalmaker.app.application.sync

import com.goalmaker.app.data.replica.text
import com.goalmaker.app.domain.sync.RowCursor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse

/**
 * An in-memory stand-in for PostgREST that behaves like the real tables: the server stamps
 * updated_at (a microsecond clock that only moves forward), keeps owner_id and created_at, returns
 * rows in (updated_at, id) order and pages by the same cursor rule.
 */
class FakeServer : RemoteTables {
    private val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
    private var clock = Instant.parse("2026-09-18T10:00:00Z")

    var offline = false
    val refusedIds = mutableSetOf<String>()
    var upserts = 0
        private set

    /** Every call, answered or not: counts sync attempts, including offline ones. */
    var calls = 0
        private set

    fun rows(table: String): Collection<JsonObject> = tables[table]?.values.orEmpty()

    /** Stores a row as if another device had pushed it. */
    fun seed(table: String, row: JsonObject): JsonObject = store(table, row)

    fun advance(seconds: Long) {
        clock = clock.plusSeconds(seconds)
    }

    override suspend fun upsert(table: String, row: JsonObject): JsonObject {
        calls++
        if (offline) throw RemoteUnavailableException("offline")
        if (row.text("id") in refusedIds) throw RemoteRejectedException("HTTP 403: row security")
        assertFalse("the server owns updated_at; clients must not send it", row.containsKey("updated_at"))
        upserts++
        return store(table, row)
    }

    override suspend fun pull(table: String, from: String?, after: RowCursor?, limit: Int): List<JsonObject> {
        calls++
        if (offline) throw RemoteUnavailableException("offline")
        return rows(table)
            .filter { from == null || it.text("updated_at")!! >= from }
            .filter { row ->
                after == null ||
                    row.text("updated_at")!! > after.updatedAt ||
                    (row.text("updated_at") == after.updatedAt && row.text("id")!! > after.id)
            }
            .sortedWith(compareBy<JsonObject>({ it.text("updated_at") }, { it.text("id") }))
            .take(limit)
    }

    private fun store(table: String, row: JsonObject): JsonObject {
        val rows = tables.getOrPut(table) { mutableMapOf() }
        val id = row.text("id")!!
        val stored = LinkedHashMap(row)
        rows[id]?.let { existing ->
            stored["owner_id"] = existing.getValue("owner_id")
            stored["created_at"] = existing.getValue("created_at")
        }
        clock = clock.plusNanos(1_000)
        // PostgREST's text form, which the engine must normalize.
        stored["updated_at"] = JsonPrimitive(postgrestFormat.format(clock))
        return JsonObject(stored).also { rows[id] = it }
    }

    private companion object {
        val postgrestFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'").withZone(ZoneOffset.UTC)
    }
}
