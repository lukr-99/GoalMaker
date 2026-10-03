package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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

    @Test
    fun `every January nudge`() {
        vectors.getValue("newYear").jsonArray.map { it.jsonObject }.forEach { case ->
            val expect = case["expect"]?.takeUnless { it == JsonNull }?.jsonObject?.let { nudge ->
                NewYearNudge(nudge.getValue("year").jsonPrimitive.int, nudge.getValue("review").jsonPrimitive.boolean, nudge.getValue("goals").jsonPrimitive.boolean)
            }
            assertEquals(
                case.getValue("name").jsonPrimitive.content,
                expect,
                ReviewRules.newYear(
                    LocalDate.parse(case.getValue("today").jsonPrimitive.content),
                    case.getValue("yearGoals").jsonPrimitive.int,
                    case.getValue("lastYearReviewed").jsonPrimitive.boolean,
                    case["dismissedYear"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
                ),
            )
        }
    }
}
