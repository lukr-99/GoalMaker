package com.goalmaker.app.domain.backup

import kotlinx.serialization.json.JsonObject

/**
 * One export: who it belongs to, what wrote it, and every row it carries, table by table
 * (docs/backup.md). Tombstones are left out, so a row may point at something the file does not
 * carry.
 */
data class BackupDocument(
    val version: Int,
    val exportedAt: String,
    val app: String,
    val appVersion: String,
    val owner: String,
    /** Rows by table, in the order a restore applies them. */
    val tables: Map<String, List<JsonObject>>,
) {
    val rowCount: Int get() = tables.values.sumOf { it.size }
}
