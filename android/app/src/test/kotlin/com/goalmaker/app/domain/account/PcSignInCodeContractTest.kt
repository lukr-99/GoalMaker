package com.goalmaker.app.domain.account

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/pc-sign-in.json, which the Windows app and the pc-sign-in function pass too. */
class PcSignInCodeContractTest {
    private val vectors = ContractFiles.load("vectors/pc-sign-in.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun cases(name: String) = vectors.getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `the prefix is the one the contract names`() {
        assertEquals(vectors.getValue("prefix").jsonPrimitive.content, PcSignInCode.PREFIX)
    }

    @Test
    fun `every secret builds its text`() {
        cases("build").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), PcSignInCode.qrText(case.text("secret")!!))
        }
    }

    @Test
    fun `every text gives up its secret or none`() {
        cases("read").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), PcSignInCode.scanSecretOf(case.text("text")!!))
        }
    }
}
