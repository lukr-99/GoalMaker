package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/life-goals.json, which the Windows app and the connector pass too. */
class LifeGoalRulesContractTest {
    private val vectors = ContractFiles.load("vectors/life-goals.json")

    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = text(name)?.let(LocalDate::parse)
    private fun JsonObject.time(name: String) = text(name)?.let(LocalDateTime::parse)
    private fun JsonObject.expectObject() = getValue("expect").takeUnless { it is JsonNull }?.jsonObject

    private fun quietHours(value: JsonObject): QuietHours {
        val text = value.text("quietHours") ?: return QuietHours.OFF
        val (start, end) = text.split("-")
        return QuietHours(LocalTime.parse(start), LocalTime.parse(end))
    }

    private fun goals(value: JsonObject) = value.getValue("lifeGoals").jsonArray.map { it.jsonObject }.map {
        LifeGoalItem(
            id = it.text("id")!!,
            title = it.text("id")!!,
            why = "Because",
            status = it.text("status")!!,
            position = it.getValue("position").jsonPrimitive.double,
            createdAt = it.text("createdAt")!!,
            closedAt = it.text("closedAt"),
        )
    }

    private fun moment(value: JsonObject?) =
        value?.let { WhyMoment(it.day("periodStart")!!, it.time("at")!!) }

    @Test
    fun `time left`() {
        for (case in vectors.cases("timeLeft")) {
            val expect = case.expectObject()?.let {
                TimeLeft(TimeLeftUnit.entries.single { unit -> unit.key == it.text("unit") }, it.getValue("count").jsonPrimitive.int)
            }
            assertEquals(case.text("name"), expect, LifeGoalRules.timeLeft(case.day("by"), case.day("today")!!))
        }
    }

    @Test
    fun order() {
        for (case in vectors.cases("order")) {
            val expect = case.getValue("expect").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(case.text("name"), expect, LifeGoalRules.ordered(goals(case)).map { it.id })
        }
    }

    @Test
    fun `the hash`() {
        for (case in vectors.cases("fnv1a")) {
            assertEquals(case.text("name"), case.getValue("expect").jsonPrimitive.long, WhyReminder.fnv1a(case.text("text")!!))
        }
    }

    @Test
    fun `the moment of a period`() {
        for (case in vectors.cases("whyMoment")) {
            val frequency = WhyFrequency.of(case.text("frequency"))
            assertEquals(
                case.text("name"),
                moment(case.expectObject()),
                WhyReminder.moment(frequency, case.day("day")!!, quietHours(case)),
            )
        }
    }

    @Test
    fun `the life goal a period shows`() {
        for (case in vectors.cases("whyGoal")) {
            val frequency = WhyFrequency.of(case.text("frequency"))
            assertEquals(
                case.text("name"),
                case.text("expect"),
                WhyReminder.goal(frequency, case.day("periodStart")!!, goals(case))?.id,
            )
        }
    }

    @Test
    fun `what a look shows`() {
        for (case in vectors.cases("whyDue")) {
            val frequency = WhyFrequency.of(case.text("frequency"))
            assertEquals(
                case.text("name"),
                moment(case.expectObject()),
                WhyReminder.due(frequency, quietHours(case), case.time("since")!!, case.time("now")!!),
            )
        }
    }

    @Test
    fun `the next moment for the alarm`() {
        for (case in vectors.cases("whyNext")) {
            val frequency = WhyFrequency.of(case.text("frequency"))
            assertEquals(
                case.text("name"),
                case.time("expect"),
                WhyReminder.next(frequency, quietHours(case), case.time("now")!!),
            )
        }
    }
}
