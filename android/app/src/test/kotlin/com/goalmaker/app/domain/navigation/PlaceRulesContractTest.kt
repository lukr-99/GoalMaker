package com.goalmaker.app.domain.navigation

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/navigation.json, which the Windows app passes too. */
class PlaceRulesContractTest {
    private val vectors = ContractFiles.load("vectors/navigation.json")

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonElement.ids() = jsonArray.map { it.jsonPrimitive.content }

    private fun device(name: String) = when (name) {
        "phone" -> DeviceKind.PHONE
        "pc" -> DeviceKind.PC
        else -> error("unknown device $name")
    }

    private fun assertResult(case: JsonObject, actual: PinResult) {
        val expect = case.getValue("expect").jsonObject
        assertEquals(case.text("name"), expect.getValue("pins").ids(), actual.pins)
        assertEquals(case.text("name"), expect.getValue("refused").jsonPrimitive.boolean, actual.refused)
    }

    @Test
    fun `the places are the ones the contract names, in its order`() {
        assertEquals(vectors.getValue("places").ids(), PlaceRules.PLACES)
    }

    @Test
    fun `limits and defaults per device`() {
        val limits = vectors.getValue("limits").jsonObject
        val defaults = vectors.getValue("defaults").jsonObject
        listOf("phone", "pc").forEach { name ->
            val limit = limits.getValue(name).let { if (it is JsonNull) null else it.jsonPrimitive.int }
            assertEquals(name, limit, PlaceRules.limit(device(name)))
            assertEquals(name, defaults.getValue(name).ids(), PlaceRules.defaults(device(name)))
        }
    }

    @Test
    fun `every pin`() {
        vectors.cases("pin").forEach { case ->
            assertResult(case, PlaceRules.pin(case.getValue("pins").ids(), case.text("place"), device(case.text("device"))))
        }
    }

    @Test
    fun `every unpin`() {
        vectors.cases("unpin").forEach { case ->
            assertResult(case, PlaceRules.unpin(case.getValue("pins").ids(), case.text("place")))
        }
    }

    @Test
    fun `every stored list`() {
        vectors.cases("stored").forEach { case ->
            val stored = case.getValue("stored").let { if (it is JsonArray) it.ids() else null }
            assertEquals(case.text("name"), case.getValue("expect").ids(), PlaceRules.stored(stored, device(case.text("device"))))
        }
    }

    @Test
    fun `every count`() {
        vectors.cases("count").forEach { case ->
            val waiting = case.getValue("waiting").jsonObject.mapValues { it.value.jsonPrimitive.int }
            assertEquals(case.text("name"), case.getValue("expect").jsonPrimitive.int, PlaceRules.count(case.getValue("pins").ids(), waiting))
        }
    }
}
