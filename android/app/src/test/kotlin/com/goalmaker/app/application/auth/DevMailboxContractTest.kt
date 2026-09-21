package com.goalmaker.app.application.auth

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/dev-mailbox.json, which the Windows app passes too. */
class DevMailboxContractTest {
    private val vectors = ContractFiles.load("vectors/dev-mailbox.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun cases(name: String) = vectors.getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `every backend finds its mailbox or none`() {
        cases("mailbox").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), DevMailbox.of(case.text("backend")))
        }
    }

    @Test
    fun `every message gives up its code or none`() {
        cases("codes").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), DevMailbox.codeIn(case.text("text")))
        }
    }
}
