package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/reviews.json, which the Windows app and the connector pass too. */
class ReviewRulesContractTest {
    private val vectors = ContractFiles.load("vectors/reviews.json")

    @Test
    fun `every review id`() {
        vectors.getValue("ids").jsonArray.map { it.jsonObject }.forEach { case ->
            fun text(name: String) = case.getValue(name).jsonPrimitive.content
            assertEquals(
                text("id"),
                ReviewRules.idOf(text("owner"), text("kind"), LocalDate.parse(text("periodStart"))),
            )
        }
    }

    @Test
    fun `every review period`() {
        vectors.getValue("periods").jsonArray.map { it.jsonObject }.forEach { case ->
            fun text(name: String) = case.getValue(name).jsonPrimitive.content
            assertEquals(
                "${text("kind")} ${text("day")}",
                LocalDate.parse(text("start")),
                ReviewRules.periodStart(text("kind"), LocalDate.parse(text("day"))),
            )
        }
    }
}
