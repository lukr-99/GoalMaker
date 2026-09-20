package com.goalmaker.app.application.backup

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.backup.BackupDocument
import com.goalmaker.app.domain.backup.BackupProblem
import com.goalmaker.app.domain.backup.BackupRules
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reading the owner's data out to a file and back in (docs/backup.md, spec story 91). The rules live
 * in [BackupRules]; this is the part that touches the replica. The methods block on disk, so callers
 * run them off the main thread.
 */
class BackupService(
    private val catalog: SyncedTableCatalog,
    private val replica: Replica,
    private val ownerId: () -> String?,
    private val appVersion: String,
    private val app: String,
    private val now: () -> Instant,
) {
    /** What the export would carry, or null when nobody is signed in. Tombstones are left out. */
    fun read(): BackupDocument? {
        val owner = ownerId() ?: return null
        val tables = catalog.tables.associate { table ->
            table.name to replica.all(table.name).filter { row -> text(row[SyncedTable.DELETED_AT]) == null }
        }
        return BackupDocument(
            version = BackupRules.VERSION,
            exportedAt = SyncRules.format(now()),
            app = app,
            appVersion = appVersion,
            owner = owner,
            tables = tables,
        )
    }

    /** The file's text, or null when nobody is signed in. */
    fun export(): String? = read()?.let { BackupRules.write(it, catalog.tables.map { table -> table.name }) }

    /** What is wrong with this text for the signed-in owner, or null when it can be restored. */
    fun check(text: String): BackupProblem? {
        val owner = ownerId() ?: return BackupProblem.ANOTHER_OWNER
        val root = parse(text) ?: return BackupProblem.NOT_A_BACKUP
        return BackupRules.check(root, owner, catalog.tables.map { it.name }.toSet())
    }

    /** What restoring this text would do, without doing it. Null when the file would be refused. */
    fun preview(text: String): RestoreReport? = weigh(text)?.first

    /**
     * Restores the file: every row it is newer for, in one transaction, queued so the rows reach the
     * other devices too. Null when the file is refused, in which case nothing was written.
     */
    fun restore(text: String, requestSync: () -> Unit = {}): RestoreReport? {
        val (report, changes) = weigh(text) ?: return null
        if (changes.isNotEmpty()) {
            replica.inTransaction { changes.forEach { (table, row) -> replica.queue(table, row) } }
            requestSync()
        }
        return report
    }

    // The decision for every row in the file: what it would do, and the rows that would be written.
    private fun weigh(text: String): Pair<RestoreReport, List<Pair<String, JsonObject>>> ? {
        if (check(text) != null) return null
        val document = parse(text)?.let(BackupRules::read) ?: return null
        var report = RestoreReport()
        val changes = mutableListOf<Pair<String, JsonObject>>()
        for (table in catalog.tables) {
            for (row in document.tables[table.name].orEmpty()) {
                val id = text(row[SyncedTable.ID]) ?: continue
                val local = replica.get(table.name, id)
                if (local == null) {
                    report += RestoreReport(added = 1)
                    changes += table.name to row
                } else if (BackupRules.takesFile(text(local[SyncedTable.UPDATED_AT]), text(row[SyncedTable.UPDATED_AT]))) {
                    report += RestoreReport(updated = 1)
                    changes += table.name to row
                } else {
                    report += RestoreReport(kept = 1)
                }
            }
        }
        return report to changes
    }

    private fun parse(text: String): JsonObject? =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun text(element: kotlinx.serialization.json.JsonElement?): String? =
        (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotEmpty() }
}
