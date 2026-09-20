package com.goalmaker.app.domain.backup

import com.goalmaker.app.domain.sync.SyncedTable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The export both apps write and read (docs/backup.md), pinned by contracts/vectors/backup.json:
 * what the file says, what a reader checks before it changes anything, and which row wins when the
 * file and the replica both have one. Pure: it never touches a file or a replica.
 */
object BackupRules {
    /** What every export says it is. */
    const val FORMAT = "goalmaker.backup"

    /** The version this build writes, and the newest it can read. */
    const val VERSION = 1

    /** The name a file is offered under, with the day it was written. */
    fun fileName(day: String): String = "goalmaker-$day.json"

    /**
     * The file as text: keys in the order below, tables in `order`, two spaces of indent and a
     * newline at the end, so an export from either app is the same bytes.
     */
    fun write(document: BackupDocument, order: List<String>): String {
        val text = StringBuilder()
        text.append("{\n")
        line(text, 1, "format", JsonPrimitive(FORMAT), last = false)
        line(text, 1, "version", JsonPrimitive(document.version), last = false)
        line(text, 1, "exportedAt", JsonPrimitive(document.exportedAt), last = false)
        line(text, 1, "app", JsonPrimitive(document.app), last = false)
        line(text, 1, "appVersion", JsonPrimitive(document.appVersion), last = false)
        line(text, 1, "owner", JsonPrimitive(document.owner), last = false)

        val tables = order.filter { document.tables.containsKey(it) }
        text.append("  \"tables\": {")
        if (tables.isEmpty()) text.append("}\n") else text.append('\n')
        tables.forEachIndexed { index, table ->
            val rows = document.tables.getValue(table)
            text.append("    ").append(quote(table)).append(": [")
            if (rows.isEmpty()) text.append(']') else text.append('\n')
            rows.forEachIndexed { row, values ->
                text.append("      {\n")
                val columns = values.entries.toList()
                columns.forEachIndexed { column, (name, value) ->
                    line(text, 4, name, value, last = column == columns.size - 1)
                }
                text.append("      }").append(if (row == rows.size - 1) "\n" else ",\n")
            }
            if (rows.isNotEmpty()) text.append("    ]") else Unit
            text.append(if (index == tables.size - 1) "\n" else ",\n")
        }
        if (tables.isNotEmpty()) text.append("  }\n")
        text.append("}\n")
        return text.toString()
    }

    /** The document a file holds, or null when it is not JSON with the parts an export has. */
    fun read(element: JsonElement): BackupDocument? {
        val root = element as? JsonObject ?: return null
        val tables = (root["tables"] as? JsonObject)?.entries?.associate { (name, rows) ->
            name to ((rows as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList())
        } ?: emptyMap()
        return BackupDocument(
            version = (root["version"] as? JsonPrimitive)?.intOrNull ?: 0,
            exportedAt = text(root["exportedAt"]).orEmpty(),
            app = text(root["app"]).orEmpty(),
            appVersion = text(root["appVersion"]).orEmpty(),
            owner = text(root["owner"]).orEmpty(),
            tables = tables,
        ).takeIf { text(root["format"]) != null || root.containsKey("tables") }
    }

    /**
     * What is wrong with this file for this owner, or null when it can be restored. The whole file
     * is judged before anything is written, so a refusal leaves the replica untouched.
     */
    fun check(root: JsonObject, owner: String, known: Set<String>): BackupProblem? {
        if (text(root["format"]) != FORMAT) return BackupProblem.NOT_A_BACKUP
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return BackupProblem.NOT_A_BACKUP
        if (version > VERSION) return BackupProblem.TOO_NEW
        if (text(root["owner"]) != owner) return BackupProblem.ANOTHER_OWNER

        val tables = root["tables"] as? JsonObject ?: return BackupProblem.NOT_A_BACKUP
        for ((table, rows) in tables) {
            if (table !in known) return BackupProblem.UNKNOWN_TABLE
            for (row in (rows as? JsonArray).orEmpty()) {
                val values = row as? JsonObject ?: return BackupProblem.ROW_WITHOUT_ID
                if (text(values[SyncedTable.ID]).isNullOrBlank()) return BackupProblem.ROW_WITHOUT_ID
                val rowOwner = text(values[SyncedTable.OWNER_ID])
                if (rowOwner != null && rowOwner != owner) return BackupProblem.ANOTHER_OWNER
            }
        }
        return null
    }

    /**
     * Whether a restore takes the file's row over the one already here, by the server timestamp
     * both carry: the rule sync uses, so a restore never undoes newer work. A row that is not here
     * is always taken.
     */
    fun takesFile(local: String?, file: String?): Boolean {
        if (local == null) return true
        if (file == null) return false
        return file > local
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: JsonArray(emptyList())

    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.takeIf { it.isString }?.contentOrNull

    private fun line(text: StringBuilder, depth: Int, name: String, value: JsonElement, last: Boolean) {
        text.append("  ".repeat(depth)).append(quote(name)).append(": ").append(value(value))
        text.append(if (last) "\n" else ",\n")
    }

    private fun value(element: JsonElement): String = when {
        element is JsonNull -> "null"
        element is JsonPrimitive && element.isString -> quote(element.content)
        element is JsonPrimitive -> element.content
        else -> element.toString()
    }

    // JSON's own escapes only: text outside ASCII is written as itself, so both apps write the same bytes.
    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (character in text) {
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '' -> out.append("\\f")
                else -> if (character < ' ') out.append("\\u%04x".format(character.code)) else out.append(character)
            }
        }
        return out.append('"').toString()
    }
}
