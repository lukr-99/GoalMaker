package com.goalmaker.app.application.activity

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/activity.json, which the Windows app passes too. */
class ActivityRulesContractTest {
    private val vectors = ContractFiles.load("vectors/activity.json")

    @Test
    fun `every activity change`() {
        vectors.getValue("cases").jsonArray.map { it.jsonObject }.forEach { case ->
            val expect = case.getValue("expect").jsonObject
            fun text(element: kotlinx.serialization.json.JsonElement?) = element?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
            val expected = ActivityChange(
                change = text(expect["change"])!!,
                subject = text(expect["subject"]),
                day = text(expect["day"]),
            )
            val actual = ActivityRules.change(
                entity = text(case["entity"])!!,
                action = text(case["action"])!!,
                before = case["before"] as? JsonObject,
                after = case.getValue("after").jsonObject,
            )
            assertEquals(text(case["name"]), expected, actual)
        }
    }
}
