package com.goalmaker.app.domain.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The synced tables in push and pull order, read from contracts/schemas/synced-tables.json. */
class SyncedTableCatalog(
    /** Parents before children, the order pushes and pulls follow. */
    val tables: List<SyncedTable>,
) {
    private val byName = tables.associateBy(SyncedTable::name)

    operator fun get(name: String): SyncedTable =
        byName[name] ?: throw NoSuchElementException("'$name' is not a synced table")

    companion object {
        fun parse(json: String): SyncedTableCatalog {
            val tables = Json.parseToJsonElement(json).jsonObject.getValue("tables").jsonArray.map { table ->
                val fields = table.jsonObject
                SyncedTable(
                    name = fields.getValue("name").jsonPrimitive.content,
                    columns = fields.getValue("columns").jsonArray.map { column ->
                        SyncedColumn(
                            name = column.jsonObject.getValue("name").jsonPrimitive.content,
                            kind = ColumnKind.parse(column.jsonObject.getValue("kind").jsonPrimitive.content),
                            required = column.jsonObject["required"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    },
                )
            }
            return SyncedTableCatalog(tables)
        }
    }
}
