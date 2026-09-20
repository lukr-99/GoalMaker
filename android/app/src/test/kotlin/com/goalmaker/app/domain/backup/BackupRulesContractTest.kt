package com.goalmaker.app.domain.backup

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** contracts/vectors/backup.json, the same file the Windows tests read. */
class BackupRulesContractTest {
    private val vectors = ContractFiles.load("vectors/backup.json")
    private val order = vectors.getValue("order").jsonArray.map { it.jsonPrimitive.content }
    private val known = order.toSet()

    @Test
    fun `this build writes the format and version the contract names`() {
        val current = vectors.getValue("current").jsonObject
        assertEquals(current.getValue("format").jsonPrimitive.content, BackupRules.FORMAT)
        assertEquals(current.getValue("version").jsonPrimitive.int, BackupRules.VERSION)
    }

    @Test
    fun `every check reaches the contract's verdict`() {
        vectors.getValue("checks").jsonArray.forEach { case ->
            val fields = case.jsonObject
            val name = fields.getValue("name").jsonPrimitive.content
            val expected = fields.getValue("expect").let { if (it is JsonNull) null else it.jsonPrimitive.content }

            val problem = BackupRules.check(
                root = fields.getValue("file").jsonObject,
                owner = fields.getValue("owner").jsonPrimitive.content,
                known = known,
            )

            assertEquals(name, expected, problem?.key)
        }
    }

    @Test
    fun `every row decision follows the contract`() {
        vectors.getValue("takesFile").jsonArray.forEach { case ->
            val fields = case.jsonObject
            val name = fields.getValue("name").jsonPrimitive.content
            fun stamp(key: String) = fields.getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.content }

            assertEquals(
                name,
                fields.getValue("expect").jsonPrimitive.boolean,
                BackupRules.takesFile(stamp("local"), stamp("file")),
            )
        }
    }

    @Test
    fun `the golden export is written and read back exactly`() {
        val golden = vectors.getValue("golden").jsonObject
        val document = golden.getValue("document").jsonObject
        val expected = golden.getValue("text").jsonPrimitive.content

        val read = BackupRules.read(document)!!
        assertEquals("1.0.0", read.appVersion)
        assertEquals(2, read.rowCount)
        assertNull(BackupRules.check(document, read.owner, known))

        assertEquals(expected, BackupRules.write(read, order))
        assertEquals(read, BackupRules.read(kotlinx.serialization.json.Json.parseToJsonElement(expected)))
    }

    @Test
    fun `an export carries the synced tables, in their own order`() {
        val tables = ContractFiles.load("schemas/synced-tables.json")
            .getValue("tables").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

        assertEquals(tables, order)
    }

    @Test
    fun `a file is named after the day it was written`() {
        assertEquals("goalmaker-2026-09-20.json", BackupRules.fileName("2026-09-20"))
    }

    private val kotlinx.serialization.json.JsonElement.int: Int get() = jsonPrimitive.content.toInt()
}
