package com.goalmaker.app.data.replica

import androidx.sqlite.driver.AndroidSQLiteDriver
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.robolectric.RuntimeEnvironment

/**
 * A real [SqliteReplica] on a throwaway file, with the schema and contract the app packages as
 * assets. Tests run under Robolectric, whose framework SQLite stands in for the bundled driver.
 */
class TestReplica : AutoCloseable {
    private val folder: File = Files.createTempDirectory("goalmaker-replica").toFile()
    private val assets = RuntimeEnvironment.getApplication().assets

    val catalog: SyncedTableCatalog = SyncedTableCatalog.parse(
        assets.open("synced-tables.json").use { it.readBytes().toString(Charsets.UTF_8) },
    )

    val replica = SqliteReplica(AndroidSQLiteDriver(), File(folder, "replica.db").path, catalog) {
        ReplicaMigrator.builtIn(assets)
    }

    /** A complete tasks row as a device would create it. */
    fun newTask(id: String, title: String, createdAt: String = "2026-09-18T08:00:00.000000Z"): JsonObject {
        val row = catalog["tasks"].columns.associateTo(LinkedHashMap<String, JsonElement>()) { it.name to JsonNull }
        row["id"] = JsonPrimitive(id)
        row["owner_id"] = JsonPrimitive(OWNER)
        row["title"] = JsonPrimitive(title)
        row["notes"] = JsonPrimitive("")
        row["top_priority"] = JsonPrimitive(false)
        row["status"] = JsonPrimitive("open")
        row["position"] = JsonPrimitive(0.0)
        row["created_at"] = JsonPrimitive(createdAt)
        row["updated_at"] = JsonPrimitive("")
        return JsonObject(row)
    }

    override fun close() {
        replica.close()
        folder.deleteRecursively()
    }

    companion object {
        const val OWNER = "11111111-1111-1111-1111-111111111111"
    }
}

/** A copy of the row with some values changed. */
fun JsonObject.with(vararg changes: Pair<String, Any?>): JsonObject = JsonObject(
    this + changes.associate { (name, value) ->
        name to when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> error("Unsupported value $value")
        }
    },
)

/** A text column's value, or null. */
fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
