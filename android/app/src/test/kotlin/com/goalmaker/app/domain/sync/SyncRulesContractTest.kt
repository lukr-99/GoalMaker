package com.goalmaker.app.domain.sync

import com.goalmaker.app.contracts.ContractFiles
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** contracts/vectors/sync-merge.json, the same file the Windows tests read. */
class SyncRulesContractTest {
    private val vectors = ContractFiles.load("vectors/sync-merge.json")

    private fun version(element: kotlinx.serialization.json.JsonElement): RowVersion? =
        if (element is JsonNull) null else (element as JsonObject).let {
            RowVersion(it.getValue("updatedAt").jsonPrimitive.content, it.getValue("deleted").jsonPrimitive.boolean)
        }

    @Test
    fun `merges every case as agreed`() {
        val cases = vectors.getValue("merge").jsonArray
        assertTrue(cases.size >= 10)
        for (element in cases) {
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val decision = SyncRules.merge(
                local = version(case.getValue("local")),
                pending = case.getValue("pending").jsonPrimitive.boolean,
                remote = version(case.getValue("remote"))!!,
            )
            val expected = case.getValue("result").jsonPrimitive.content == "take-remote"
            assertEquals(name, expected, decision.takeRemote)
            assertEquals(name, case.getValue("dropPending").jsonPrimitive.boolean, decision.dropPending)
        }
    }

    @Test
    fun `starts over only when never synced or more than 80 days behind`() {
        for (element in vectors.getValue("fullResync").jsonArray) {
            val case = element.jsonObject
            assertEquals(
                case.getValue("name").jsonPrimitive.content,
                case.getValue("full").jsonPrimitive.boolean,
                SyncRules.needsFullResync(
                    case.getValue("watermark").jsonPrimitive.contentOrNull,
                    Instant.parse(case.getValue("now").jsonPrimitive.content),
                ),
            )
        }
    }

    @Test
    fun `pulls from a minute before the watermark`() {
        for (element in vectors.getValue("pullFrom").jsonArray) {
            val case = element.jsonObject
            assertEquals(
                case.getValue("from").jsonPrimitive.contentOrNull,
                SyncRules.pullFrom(case.getValue("watermark").jsonPrimitive.contentOrNull),
            )
        }
    }

    @Test
    fun `normalizes server timestamps to sortable UTC text`() {
        for (element in vectors.getValue("normalizeTimestamp").jsonArray) {
            val case = element.jsonObject
            val input = case.getValue("input").jsonPrimitive.content
            assertEquals(input, case.getValue("output").jsonPrimitive.contentOrNull, SyncRules.normalizeTimestamp(input))
        }
    }
}
