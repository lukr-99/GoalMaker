package com.goalmaker.app.application.sync

import com.goalmaker.app.domain.sync.ColumnKind
import com.goalmaker.app.domain.sync.RowCursor
import com.goalmaker.app.domain.sync.RowVersion
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * One sync run: push the outbox in order, then pull every table and merge (docs/sync.md). Decisions
 * come from [SyncRules], so both apps behave the same. Replica calls block, so this runs on an IO
 * dispatcher; not safe to run twice at once, which the [SyncCoordinator] prevents.
 *
 * [pulls] is false only for a dev build's local-only remote, which never has anything to send back:
 * a table that has never pulled has no watermark, and no watermark means a full resync, which clears
 * the table first, so pulling from it would wipe the replica on every run.
 */
class SyncEngine(
    private val catalog: SyncedTableCatalog,
    private val replica: Replica,
    private val remote: RemoteTables,
    private val pulls: Boolean = true,
    private val now: () -> Instant,
) {
    suspend fun run(): SyncReport {
        var pushed = 0
        var rejected = 0
        var pulled = 0
        return try {
            for (entry in replica.outbox()) {
                val table = catalog[entry.entity]
                try {
                    // The server owns updated_at (docs/sync.md), so it is never sent.
                    val row = JsonObject(Json.parseToJsonElement(entry.payload).jsonObject - SyncedTable.UPDATED_AT)
                    val stored = remote.upsert(entry.entity, row)
                    replica.completePush(entry, normalize(table, stored))
                    pushed++
                } catch (error: RemoteRejectedException) {
                    replica.failPush(entry, error.message.orEmpty())
                    rejected++
                }
            }

            if (pulls) {
                for (table in catalog.tables) {
                    pulled += pull(table)
                }
            }

            SyncReport(
                pushed = pushed,
                rejected = rejected,
                pulled = pulled,
                offline = false,
                problem = if (rejected > 0) "$rejected change(s) refused by the server" else null,
            )
        } catch (error: RemoteUnavailableException) {
            SyncReport(pushed, rejected, pulled, offline = true, problem = error.message)
        }
    }

    private suspend fun pull(table: SyncedTable): Int {
        var watermark = replica.watermark(table.name)
        val full = SyncRules.needsFullResync(watermark, now())
        if (full) {
            replica.clearSynced(table.name)
            watermark = null
        }

        val from = if (full) null else SyncRules.pullFrom(watermark)
        var cursor: RowCursor? = null
        var newest = watermark
        var count = 0
        while (true) {
            val page = remote.pull(table.name, from, cursor, PAGE_SIZE)
            if (page.isEmpty()) break

            val rows = page.map { normalize(table, it) }
            replica.inTransaction { rows.forEach { merge(table.name, it) } }
            count += rows.size

            val last = rows.last()
            val lastUpdated = last.text(SyncedTable.UPDATED_AT).orEmpty()
            if (newest == null || lastUpdated > newest) {
                newest = lastUpdated
            }

            if (page.size < PAGE_SIZE) break
            cursor = RowCursor(lastUpdated, last.text(SyncedTable.ID).orEmpty())
        }

        newest?.let { replica.setWatermark(table.name, it) }
        return count
    }

    private fun merge(table: String, remoteRow: JsonObject) {
        val id = remoteRow.text(SyncedTable.ID).orEmpty()
        val local = replica.get(table, id)
        val decision = SyncRules.merge(
            local = local?.let(::versionOf),
            pending = replica.isPending(table, id),
            remote = versionOf(remoteRow),
        )
        if (decision.dropPending) replica.dropPending(table, id)
        if (decision.takeRemote) replica.put(table, remoteRow)
    }

    companion object {
        const val PAGE_SIZE = 500

        /** Keeps exactly the described columns and copies server timestamps into the normalized form. */
        fun normalize(table: SyncedTable, row: JsonObject): JsonObject = JsonObject(
            table.columns.associate { column ->
                val value = row[column.name] ?: JsonNull
                column.name to if (column.kind == ColumnKind.TIMESTAMP && value is JsonPrimitive && value.isString) {
                    JsonPrimitive(SyncRules.normalizeTimestamp(value.content) ?: value.content)
                } else {
                    value
                }
            },
        )

        private fun versionOf(row: JsonObject) = RowVersion(
            updatedAt = row.text(SyncedTable.UPDATED_AT).orEmpty(),
            deleted = row[SyncedTable.DELETED_AT].let { it != null && it != JsonNull },
        )

        private fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    }
}
