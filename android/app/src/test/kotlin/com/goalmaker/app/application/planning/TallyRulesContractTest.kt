package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/tally.json, which the Windows app and the connector pass too. */
class TallyRulesContractTest {
    private val vectors = ContractFiles.load("vectors/tally.json")
    private val shipped = TallyDefaults.parse(ContractFiles.load("content/tally-rules.json").toString())

    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.rules(name: String) = this[name]?.jsonArray?.map { TallyDefaults.parseRule(it.jsonObject) }.orEmpty()

    private fun folders(value: JsonObject) = value["projects"]?.jsonArray.orEmpty()
        .associate { it.jsonObject.text("id")!! to it.jsonObject.text("localFolder") }

    @Test
    fun `every sample goes where the contract says`() {
        vectors.cases("match").forEach { case ->
            val sample = case.getValue("sample").jsonObject
            val defaults = if (case["defaults"] is JsonArray) case.rules("defaults") else shipped.rules
            val sorted = TallyRules.sortSample(
                TallySample(sample.text("platform")!!, sample.text("app")!!, sample.text("title")),
                case.rules("own"),
                defaults,
                folders(case),
            )
            val expect = case.getValue("expect").jsonObject
            assertEquals(case.text("name"), TallySort(expect.text("category")!!, expect.text("project")), sorted)
        }
    }

    @Test
    fun `every editor folder`() {
        vectors.cases("folder").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), TallyRules.editorFolder(case.text("app")!!, case.text("title")))
        }
    }

    @Test
    fun `every project from a folder`() {
        vectors.cases("project").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), TallyRules.projectFor(case.text("folder"), folders(case)))
        }
    }

    @Test
    fun `every idle moment`() {
        vectors.cases("idle").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.boolean,
                TallyRules.counts(
                    case.getValue("secondsSinceInput").jsonPrimitive.long,
                    case.text("category")!!,
                    case.getValue("locked").jsonPrimitive.boolean,
                    case.getValue("asleep").jsonPrimitive.boolean,
                ),
            )
        }
    }

    @Test
    fun `every day's totals`() {
        vectors.cases("days").forEach { case ->
            val intervals = case.getValue("intervals").jsonArray.map { it.jsonObject }.map { interval ->
                TallyInterval(
                    LocalDateTime.parse(interval.text("start")!!),
                    LocalDateTime.parse(interval.text("end")!!),
                    interval.text("category")!!,
                    interval.text("project"),
                )
            }
            val expect = case.getValue("expect").jsonArray.map { it.jsonObject }.map { total ->
                TallyTotal(LocalDate.parse(total.text("day")!!), total.text("category")!!, total.text("project"), total.getValue("minutes").jsonPrimitive.int)
            }
            assertEquals(case.text("name"), expect, TallyRules.dayTotals(intervals, case.getValue("startHour").jsonPrimitive.int))
        }
    }

    @Test
    fun `every tally day id`() {
        assertEquals("b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91", vectors.text("namespace"))
        vectors.cases("ids").forEach { case ->
            assertEquals(
                case.text("expect"),
                TallyRules.dayId(case.text("owner")!!, LocalDate.parse(case.text("day")!!), case.text("device")!!, case.text("category")!!, case.text("project")),
            )
        }
    }

    @Test
    fun `every stats week`() {
        vectors.cases("weeks").forEach { case ->
            val rows = case.getValue("rows").jsonArray.map { it.jsonObject }.mapIndexed { index, row ->
                TallyDay(
                    id = "row-$index",
                    day = LocalDate.parse(row.text("day")!!),
                    device = row.text("deviceKind")!!,
                    deviceKind = row.text("deviceKind")!!,
                    category = row.text("category")!!,
                    project = row.text("projectId"),
                    minutes = row.getValue("minutes").jsonPrimitive.int,
                )
            }
            val filter = case.getValue("filter").jsonObject
            val expect = case.getValue("expect").jsonArray.map { it.jsonObject }.map { week ->
                TallyWeek(
                    LocalDate.parse(week.text("start")!!),
                    week.getValue("minutes").jsonPrimitive.int,
                    week.getValue("categories").jsonArray.map { it.jsonObject }.map { TallyMinutes(it.text("category")!!, it.getValue("minutes").jsonPrimitive.int) },
                )
            }
            assertEquals(
                case.text("name"),
                expect,
                TallyRules.weeks(
                    rows,
                    LocalDate.parse(case.text("today")!!),
                    case.getValue("count").jsonPrimitive.int,
                    TallyFilter(filter.text("kind"), filter.text("category")),
                ),
            )
        }
    }

    @Test
    fun `the shipped defaults read in full`() {
        assertEquals(TallyRules.OTHER, shipped.categories.last().id)
        assertEquals(TallyRules.VIDEO, shipped.rules.first { it.pattern == "com.google.android.youtube" }.category)
        shipped.rules.forEach { rule ->
            assertTrue(rule.pattern, rule.match in TallyRules.MATCHES && rule.platform in TallyRules.PLATFORMS)
            assertTrue(rule.pattern, shipped.categories.any { it.id == rule.category })
        }
    }
}
