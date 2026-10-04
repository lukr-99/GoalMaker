package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

/** Runs the habit reminder cases of contracts/vectors/reminders.json, which the Windows app passes too. */
class HabitReminderContractTest {
    private val vectors = ContractFiles.load("vectors/reminders.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.habit(): HabitItem {
        val habit = getValue("habit").jsonObject
        return HabitItem(
            id = "h",
            name = "Habit",
            startsOn = LocalDate.parse(habit.text("startsOn")),
            cadence = habit.text("cadence")!!,
            weekdays = habit["weekdays"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
            times = habit["times"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
            measure = habit.text("measure")!!,
            target = habit["target"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double,
            direction = habit.text("direction") ?: HabitRules.AT_LEAST,
            remindAt = habit.text("remindAt")?.let(LocalTime::parse),
        )
    }

    private fun JsonObject.checkins() = getValue("checkins").jsonArray.mapIndexed { index, element ->
        val checkin = element.jsonObject
        HabitCheckin(
            id = "c$index",
            habitId = "h",
            day = LocalDate.parse(checkin.text("day")),
            value = checkin.getValue("value").jsonPrimitive.double,
            skipped = checkin.getValue("skipped").jsonPrimitive.boolean,
            failed = checkin["failed"]?.jsonPrimitive?.boolean ?: false,
        )
    }

    private fun JsonObject.pauses() = getValue("pauses").jsonArray.mapIndexed { index, element ->
        val pause = element.jsonObject
        HabitPause("p$index", "h", LocalDate.parse(pause.text("from")), pause.text("until")?.let(LocalDate::parse))
    }

    @Test
    fun `every habit reminder's day and next moment`() {
        vectors.cases("habitReminders").forEach { case ->
            val name = case.text("name")
            val hour = case.getValue("dayStartHour").jsonPrimitive.int
            val now = LocalDateTime.parse(case.text("now"))
            assertEquals(
                "$name: due",
                case.text("due")?.let(LocalDate::parse),
                HabitReminder.due(case.habit(), case.checkins(), case.pauses(), hour, LocalDateTime.parse(case.text("since")), now),
            )
            assertEquals(
                "$name: next",
                case.text("next")?.let(LocalDateTime::parse),
                HabitReminder.next(case.habit(), case.checkins(), case.pauses(), hour, now),
            )
        }
    }

    @Test
    fun `every stale habit reminder`() {
        vectors.cases("habitReminderStale").forEach { case ->
            assertEquals(
                case.text("name"),
                case.getValue("expect").jsonPrimitive.boolean,
                HabitReminder.stale(
                    case.habit(),
                    LocalDate.parse(case.text("day")),
                    case.checkins(),
                    case.pauses(),
                    case.getValue("dayStartHour").jsonPrimitive.int,
                    LocalDateTime.parse(case.text("now")),
                ),
            )
        }
    }
}
