package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/wants.json, which the Windows app and the connector pass too. */
class WantRulesContractTest {
    private val vectors = ContractFiles.load("vectors/wants.json")

    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = text(name)?.let(LocalDate::parse)
    private fun JsonElement?.number() = this?.takeUnless { it is JsonNull }?.jsonPrimitive?.double

    private fun cooldowns(value: JsonObject) = WantCooldowns(
        smallUnder = value.getValue("smallUnder").jsonPrimitive.double,
        smallDays = value.getValue("smallDays").jsonPrimitive.int,
        mediumUnder = value.getValue("mediumUnder").jsonPrimitive.double,
        mediumDays = value.getValue("mediumDays").jsonPrimitive.int,
        largeDays = value.getValue("largeDays").jsonPrimitive.int,
        unpricedDays = value.getValue("unpricedDays").jsonPrimitive.int,
        currency = value.text("currency")!!,
    )

    private fun want(value: JsonObject, today: LocalDate = LocalDate.of(2026, 9, 28)): WantItem {
        val coolsUntil = value.day("coolsUntil") ?: today
        val addedOn = value.day("addedOn") ?: coolsUntil
        return WantItem(
            id = value.text("id") ?: "w",
            title = value.text("title") ?: "Want",
            reason = "Because",
            cooldownDays = java.time.temporal.ChronoUnit.DAYS.between(addedOn, coolsUntil).toInt(),
            addedOn = addedOn,
            coolsUntil = coolsUntil,
            price = value["price"].number(),
            currency = value.text("currency") ?: "CZK",
            decision = value.text("decision"),
            deleted = value["deleted"]?.jsonPrimitive?.boolean ?: false,
            kind = value.text("kind") ?: WantRules.WANT,
            needBy = value.day("needBy"),
        )
    }

    @Test
    fun `the defaults are the ones the contract names`() {
        assertEquals(cooldowns(vectors.getValue("defaults").jsonObject), WantCooldowns.DEFAULT)
    }

    @Test
    fun `every cooldown`() {
        vectors.cases("cooldown").forEach { case ->
            val thresholds = case["cooldowns"]?.jsonObject?.let(::cooldowns) ?: WantCooldowns.DEFAULT
            val picked = case["picked"]?.jsonPrimitive?.int
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.int,
                WantRules.cooldownDays(case["price"].number(), case.text("currency")!!, thresholds, picked, case.text("kind") ?: WantRules.WANT),
            )
        }
    }

    @Test
    fun `every day it cools`() {
        vectors.cases("coolsUntil").forEach { case ->
            assertEquals(
                case.text("name"),
                case.day("expect"),
                WantRules.coolsUntil(case.day("addedOn")!!, case.getValue("days").jsonPrimitive.int),
            )
        }
    }

    @Test
    fun `every state`() {
        vectors.cases("state").forEach { case ->
            val today = PlanningDay.of(LocalDateTime.parse(case.text("now")!!), case.getValue("dayStartHour").jsonPrimitive.int)
            assertEquals(case.text("name"), case.text("expect"), WantRules.state(want(case.getValue("want").jsonObject), today)?.id)
        }
    }

    @Test
    fun `every ring`() {
        vectors.cases("progress").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.double,
                WantRules.progress(want(case.getValue("want").jsonObject), case.day("today")!!),
                1e-9,
            )
        }
    }

    @Test
    fun `every notification`() {
        vectors.cases("ready").forEach { case ->
            val wants = case.getValue("wants").jsonArray.map { want(it.jsonObject) }
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonArray.map { it.jsonPrimitive.content },
                WantRules.ready(wants, case.day("lastNotified"), case.day("today")!!).map(WantItem::id),
            )
        }
    }

    @Test
    fun `every stats block`() {
        vectors.cases("stats").forEach { case ->
            val expect = case.getValue("expect").jsonObject
            val stats = WantRules.stats(case.getValue("wants").jsonArray.map { want(it.jsonObject) }, case.text("currency")!!)
            assertEquals(case.text("name"), expect.getValue("bought").jsonPrimitive.int, stats.bought)
            assertEquals(case.text("name"), expect.getValue("dropped").jsonPrimitive.int, stats.dropped)
            assertEquals(case.text("name"), expect.getValue("notSpent").jsonPrimitive.double, stats.notSpent, 1e-9)
        }
    }

    @Test
    fun `every thresholds id`() {
        assertEquals("b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91", vectors.text("namespace"))
        vectors.cases("cooldownsId").forEach { case ->
            assertEquals(case.text("name"), case.text("expect"), WantRules.cooldownsId(case.text("owner")!!))
        }
    }

    @Test
    fun `every notification moment`() {
        vectors.cases("notify").forEach { case ->
            val wants = case.getValue("wants").jsonArray.map { want(it.jsonObject) }
            val due = WantReminder.due(
                case.text("time")?.let(LocalTime::parse),
                case.getValue("dayStartHour").jsonPrimitive.int,
                wants,
                LocalDateTime.parse(case.text("since")!!),
                LocalDateTime.parse(case.text("now")!!),
            )
            val expect = case["expect"]?.takeUnless { it is JsonNull }?.jsonObject
            assertEquals(case.text("name"), expect?.let { WantsDue(LocalDate.parse(it.text("day")!!), it.getValue("wants").jsonArray.map { id -> id.jsonPrimitive.content }) }, due)
        }
    }

    @Test
    fun `every alarm for the notification`() {
        vectors.cases("notifyNext").forEach { case ->
            val wants = case.getValue("wants").jsonArray.map { want(it.jsonObject) }
            val next = WantReminder.next(
                case.text("time")?.let(LocalTime::parse),
                case.getValue("dayStartHour").jsonPrimitive.int,
                wants,
                LocalDateTime.parse(case.text("now")!!),
            )
            assertEquals(case.text("name"), case.text("expect")?.let(LocalDateTime::parse), next)
        }
    }

    @Test
    fun `every stale notification`() {
        vectors.cases("notifyStale").forEach { case ->
            val wants = case.getValue("wants").jsonArray.map { want(it.jsonObject) }
            val shown = case.getValue("shown").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(case.text("name"), case.getValue("expect").jsonPrimitive.boolean, WantReminder.stale(shown, wants))
        }
    }

    @Test
    fun `every list of open needs`() {
        vectors.cases("needs").forEach { case ->
            val wants = case.getValue("wants").jsonArray.map { want(it.jsonObject) }
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonArray.map { it.jsonPrimitive.content },
                WantRules.needs(wants).map(WantItem::id),
            )
        }
    }

    @Test
    fun `every late need`() {
        vectors.cases("needLate").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.boolean,
                WantRules.needLate(want(case.getValue("want").jsonObject), case.day("today")!!),
            )
        }
    }
}
