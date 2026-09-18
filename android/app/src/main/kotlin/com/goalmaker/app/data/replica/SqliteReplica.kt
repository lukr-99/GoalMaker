package com.goalmaker.app.data.replica

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.ColumnKind
import com.goalmaker.app.domain.sync.OutboxEntry
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedColumn
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [Replica] on one SQLite file with the shared schema (ADR 0007). SQL is built from the
 * synced-table contract, so a new column needs no code here. The file opens and migrates on first
 * use, which is always off the main thread. All access is serialized; transactions nest, and
 * change counters move after the outermost commit.
 */
class SqliteReplica(
    private val driver: SQLiteDriver,
    private val path: String,
    private val catalog: SyncedTableCatalog,
    private val migrations: () -> List<ReplicaMigration>,
) : Replica, AutoCloseable {
    private val lock = ReentrantLock()
    private val changedInTransaction = mutableSetOf<String>()
    private val versions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var opened: SQLiteConnection? = null
    private var depth = 0

    override fun watch(table: String): Flow<Long> {
        val name = catalog[table].name
        return versions.map { it[name] ?: 0L }.distinctUntilChanged()
    }

    override fun get(table: String, id: String): JsonObject? {
        val definition = catalog[table]
        return query("SELECT ${columnList(definition)} FROM ${definition.name} WHERE id = ?", id) { row ->
            if (row.step()) readRow(definition, row) else null
        }
    }

    override fun all(table: String): List<JsonObject> {
        val definition = catalog[table]
        return query("SELECT ${columnList(definition)} FROM ${definition.name}") { row ->
            buildList { while (row.step()) add(readRow(definition, row)) }
        }
    }

    override fun queue(table: String, row: JsonObject) = inTransaction {
        put(table, row)
        val id = (row[SyncedTable.ID] as JsonPrimitive).content
        val payload = row.toString()
        // One entry per row, kept at its first position so parents still push before children.
        if (isPending(table, id)) {
            execute("UPDATE outbox SET payload = ?, attempts = 0, last_error = NULL WHERE entity = ? AND row_id = ?", payload, table, id)
        } else {
            execute(
                "INSERT INTO outbox(entity, row_id, payload, queued_at) VALUES (?, ?, ?, ?)",
                table,
                id,
                payload,
                SyncRules.format(Instant.now()),
            )
        }
    }

    override fun isPending(table: String, id: String): Boolean =
        query("SELECT 1 FROM outbox WHERE entity = ? AND row_id = ? LIMIT 1", table, id) { it.step() }

    override fun pendingCount(): Int = query("SELECT COUNT(*) FROM outbox") { it.step(); it.getLong(0).toInt() }

    override fun outbox(): List<OutboxEntry> =
        query("SELECT seq, entity, row_id, payload, attempts, last_error FROM outbox ORDER BY seq") { row ->
            buildList {
                while (row.step()) {
                    add(
                        OutboxEntry(
                            seq = row.getLong(0),
                            entity = row.getText(1),
                            rowId = row.getText(2),
                            payload = row.getText(3),
                            attempts = row.getLong(4).toInt(),
                            lastError = if (row.isNull(5)) null else row.getText(5),
                        ),
                    )
                }
            }
        }

    override fun completePush(entry: OutboxEntry, serverRow: JsonObject) = inTransaction {
        val current = query("SELECT payload FROM outbox WHERE seq = ?", entry.seq) { if (it.step()) it.getText(0) else null }
        // Edited again while the push was in flight: keep the newer local row and push it next.
        if (current == entry.payload) {
            put(entry.entity, serverRow)
            execute("DELETE FROM outbox WHERE seq = ?", entry.seq)
        }
    }

    override fun failPush(entry: OutboxEntry, error: String) =
        execute("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE seq = ?", error, entry.seq)

    override fun put(table: String, row: JsonObject) = inTransaction {
        val definition = catalog[table]
        val names = definition.columns.map(SyncedColumn::name)
        val updates = names.filter { it != SyncedTable.ID }.joinToString { "$it = excluded.$it" }
        execute(
            "INSERT INTO ${definition.name} (${names.joinToString()}) VALUES (${names.joinToString { "?" }}) " +
                "ON CONFLICT(id) DO UPDATE SET $updates",
            *definition.columns.map { toSqlite(it, row[it.name]) }.toTypedArray(),
        )
        changedInTransaction += definition.name
    }

    override fun dropPending(table: String, id: String) =
        execute("DELETE FROM outbox WHERE entity = ? AND row_id = ?", table, id)

    override fun watermark(table: String): String? =
        query("SELECT watermark FROM sync_state WHERE entity = ?", table) { if (it.step() && !it.isNull(0)) it.getText(0) else null }

    override fun setWatermark(table: String, watermark: String) = execute(
        "INSERT INTO sync_state(entity, watermark, last_pulled_at) VALUES (?, ?, ?) " +
            "ON CONFLICT(entity) DO UPDATE SET watermark = excluded.watermark, last_pulled_at = excluded.last_pulled_at",
        table,
        watermark,
        SyncRules.format(Instant.now()),
    )

    override fun clearSynced(table: String) = inTransaction {
        val name = catalog[table].name
        execute("DELETE FROM $name WHERE id NOT IN (SELECT row_id FROM outbox WHERE entity = ?)", name)
        execute("DELETE FROM sync_state WHERE entity = ?", name)
        changedInTransaction += name
    }

    override fun clearAll() = inTransaction {
        for (table in catalog.tables) {
            execute("DELETE FROM ${table.name}")
            changedInTransaction += table.name
        }
        execute("DELETE FROM outbox")
        execute("DELETE FROM sync_state")
    }

    override fun <T> inTransaction(work: () -> T): T {
        var changed = emptySet<String>()
        val result = lock.withLock {
            if (depth == 0) connection().execSQL("BEGIN IMMEDIATE TRANSACTION")
            depth++
            try {
                val value = work()
                depth--
                if (depth == 0) {
                    connection().execSQL("COMMIT")
                    changed = changedInTransaction.toSet()
                    changedInTransaction.clear()
                }
                value
            } catch (error: Throwable) {
                depth--
                if (depth == 0) {
                    connection().execSQL("ROLLBACK")
                    changedInTransaction.clear()
                }
                throw error
            }
        }

        if (changed.isNotEmpty()) {
            versions.update { current -> current + changed.associateWith { (current[it] ?: 0L) + 1 } }
        }
        return result
    }

    override fun close() = lock.withLock {
        opened?.close()
        opened = null
    }

    // Callers hold the lock.
    private fun connection(): SQLiteConnection = opened ?: run {
        File(path).absoluteFile.parentFile?.mkdirs()
        driver.open(path).also { connection ->
            try {
                connection.prepare("PRAGMA journal_mode = WAL").use { it.step() }
                ReplicaMigrator.apply(connection, migrations())
            } catch (error: Throwable) {
                connection.close()
                throw error
            }
            opened = connection
        }
    }

    private fun <T> query(sql: String, vararg arguments: Any?, read: (SQLiteStatement) -> T): T = lock.withLock {
        connection().prepare(sql).use { statement ->
            bind(statement, arguments)
            read(statement)
        }
    }

    private fun execute(sql: String, vararg arguments: Any?) = query(sql, *arguments) { it.step() }.let { }

    private companion object {
        fun columnList(table: SyncedTable) = table.columns.joinToString { it.name }

        fun bind(statement: SQLiteStatement, arguments: Array<out Any?>) {
            arguments.forEachIndexed { index, value ->
                val position = index + 1
                when (value) {
                    null -> statement.bindNull(position)
                    is String -> statement.bindText(position, value)
                    is Long -> statement.bindLong(position, value)
                    is Int -> statement.bindLong(position, value.toLong())
                    is Double -> statement.bindDouble(position, value)
                    else -> error("Can't bind ${value::class.simpleName}")
                }
            }
        }

        fun readRow(table: SyncedTable, row: SQLiteStatement): JsonObject = JsonObject(
            table.columns.withIndex().associate { (index, column) ->
                column.name to when {
                    row.isNull(index) -> JsonNull
                    column.kind == ColumnKind.BOOLEAN -> JsonPrimitive(row.getLong(index) != 0L)
                    column.kind == ColumnKind.INTEGER -> JsonPrimitive(row.getLong(index))
                    column.kind == ColumnKind.REAL -> JsonPrimitive(row.getDouble(index))
                    else -> JsonPrimitive(row.getText(index))
                }
            },
        )

        // Through the JSON text, so values parsed from the server and values made in code convert alike.
        fun toSqlite(column: SyncedColumn, value: JsonElement?): Any? {
            if (value == null || value == JsonNull) return null
            val primitive = value as? JsonPrimitive
            return when (column.kind) {
                ColumnKind.BOOLEAN -> if (primitive != null && !primitive.isString && primitive.content == "true") 1L else 0L
                ColumnKind.INTEGER -> BigDecimal(primitive?.content ?: value.toString()).toLong()
                ColumnKind.REAL -> (primitive?.content ?: value.toString()).toDouble()
                else -> if (primitive != null && primitive.isString) primitive.content else value.toString()
            }
        }
    }
}
