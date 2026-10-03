package com.goalmaker.app.domain.account

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/sign-in-code.json, which the Windows app passes too. */
class SignInCodeContractTest {
    private val vectors = ContractFiles.load("vectors/sign-in-code.json")

    private fun cases(name: String) = vectors.getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `every typed code`() {
        cases("parse").forEach { case ->
            val text = case.getValue("text").jsonPrimitive.content
            val expect = case.getValue("expect").takeUnless { it == JsonNull }?.jsonPrimitive?.content
            assertEquals(text, expect, SignInCode.parse(text)?.value)
        }
    }

    @Test
    fun `every code found in copied text`() {
        cases("find").forEach { case ->
            val expect = case.getValue("expect").takeUnless { it == JsonNull }?.jsonPrimitive?.content
            assertEquals(case.getValue("name").jsonPrimitive.content, expect, SignInCode.find(case.getValue("text").jsonPrimitive.content)?.value)
        }
    }
}
