package com.goalmaker.app.application.auth

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/dev-sign-in.json, which the Windows app passes too. */
class DevSignInContractTest {
    private val vectors = ContractFiles.load("vectors/dev-sign-in.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun cases(name: String) = vectors.getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `the dev account is the one the contract names`() {
        assertEquals(vectors.getValue("account").jsonObject.text("email"), DevSignIn.EMAIL)
    }

    @Test
    fun `every backend finds its mailbox or none`() {
        cases("mailbox").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), DevSignIn.mailboxOf(case.text("backend")))
        }
    }

    @Test
    fun `every message gives up its code or none`() {
        cases("codes").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), DevSignIn.codeIn(case.text("text")))
        }
    }
}
