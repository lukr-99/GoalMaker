package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.Snooze
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/reminders.json, which the Windows app passes too. */
class ReminderRulesContractTest {
    private val vectors = ContractFiles.load("vectors/reminders.json")

    @Test
    fun `every due time`() {
        val cases = vectors.getValue("due").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val task = case.getValue("task").jsonObject
            val actual = ReminderRules.due(reminder(case), task(task))
            val expected = dateTime(case.getValue("due"))
            if (actual == expected) null else "${name(case)}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `every quiet hours window`() {
        val cases = vectors.getValue("quietHours").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val window = QuietHours(time(case.getValue("start"))!!, time(case.getValue("end"))!!)
            val actual = window.release(dateTime(case.getValue("at"))!!, case.getValue("important").jsonPrimitive.boolean)
            val expected = dateTime(case.getValue("fires"))
            if (actual == expected) null else "${name(case)}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `every snooze`() {
        val cases = vectors.getValue("snooze").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val option = when (val name = case.getValue("option").jsonPrimitive.content) {
                "tenMinutes" -> Snooze.TEN_MINUTES
                "oneHour" -> Snooze.ONE_HOUR
                "tomorrowMorning" -> Snooze.TOMORROW_MORNING
                else -> error("Unknown snooze option: $name")
            }
            val actual = option.target(dateTime(case.getValue("now"))!!, case.getValue("dayStartHour").jsonPrimitive.int)
            val expected = dateTime(case.getValue("at"))
            if (actual == expected) null else "${name(case)}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `the morning hour matches the vectors`() {
        assertEquals(vectors.getValue("morningHour").jsonPrimitive.int, Snooze.MORNING_HOUR)
    }

    private fun reminder(case: JsonObject) = ReminderItem(
        id = "11111111-2222-4333-8444-555555555555",
        taskId = "66666666-7777-4888-8999-aaaaaaaaaaaa",
        state = when (val state = case.getValue("state").jsonPrimitive.content) {
            "pending" -> ReminderState.PENDING
            "snoozed" -> ReminderState.SNOOZED
            "dismissed" -> ReminderState.DISMISSED
            "done" -> ReminderState.DONE
            else -> error("Unknown reminder state: $state")
        },
        important = case["important"]?.jsonPrimitive?.boolean ?: false,
        fireAt = dateTime(case.getValue("fireAt")),
        offsetMinutes = case.getValue("offsetMinutes").let { if (it == JsonNull) null else it.jsonPrimitive.int },
        snoozedUntil = dateTime(case.getValue("snoozedUntil")),
        deleted = case.getValue("reminderDeleted").jsonPrimitive.boolean,
    )

    private fun task(case: JsonObject) = TaskItem(
        id = "66666666-7777-4888-8999-aaaaaaaaaaaa",
        title = "Take the bread out",
        state = when (val status = case.getValue("status").jsonPrimitive.content) {
            "done" -> TaskState.DONE
            "dropped" -> TaskState.DROPPED
            else -> TaskState.OPEN
        },
        topPriority = false,
        createdAt = "2026-09-10T08:00:00.000000Z",
        plannedDate = date(case.getValue("planned")),
        plannedTime = time(case.getValue("time")),
        deleted = case.getValue("deleted").jsonPrimitive.boolean,
    )

    private fun name(case: JsonObject) = case.getValue("name").jsonPrimitive.content

    private fun date(value: JsonElement) = if (value == JsonNull) null else LocalDate.parse(value.jsonPrimitive.content)

    private fun time(value: JsonElement) = if (value == JsonNull) null else LocalTime.parse(value.jsonPrimitive.content)

    private fun dateTime(value: JsonElement) = if (value == JsonNull) null else LocalDateTime.parse(value.jsonPrimitive.content)
}
