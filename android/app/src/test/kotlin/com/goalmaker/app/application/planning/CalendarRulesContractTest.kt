package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/calendar.json, which the Windows app passes too. */
class CalendarRulesContractTest {
    private val vectors = ContractFiles.load("vectors/calendar.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.day(name: String) = LocalDate.parse(text(name)!!)
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }

    @Test
    fun `every grid`() {
        vectors.cases("grids").forEach { case ->
            val kind = case.text("kind")!!
            val day = case.day("day")
            val name = "$kind ${case.text("day")}"
            assertEquals(name, case.day("start"), CalendarRules.start(kind, day))
            assertEquals(name, case.day("end"), CalendarRules.end(kind, day))
            val days = CalendarRules.days(kind, day)
            assertEquals(name, case.getValue("count").jsonPrimitive.int, days.size)
            assertEquals(name, case.day("start"), days.first())
            assertEquals(name, case.day("end"), days.last())
        }
    }

    @Test
    fun `every day of the calendar`() {
        vectors.cases("days").forEach { case ->
            val name = case.text("name")
            val tasks = case.getValue("tasks").jsonArray.map { element ->
                val task = element.jsonObject
                TaskItem(
                    id = task.text("id")!!,
                    title = task.text("id")!!,
                    state = when (task.text("state")) {
                        "done" -> TaskState.DONE
                        "dropped" -> TaskState.DROPPED
                        else -> TaskState.OPEN
                    },
                    topPriority = false,
                    createdAt = task.text("createdAt")!!,
                    plannedDate = task.text("plannedDate")?.let(LocalDate::parse),
                    plannedTime = task.text("plannedTime")?.let(LocalTime::parse),
                    deadline = task.text("deadline")?.let(LocalDate::parse),
                    recurrence = task.text("recurrence"),
                    seriesId = task.text("seriesId"),
                    deleted = task["deleted"]?.jsonPrimitive?.boolean ?: false,
                )
            }
            val reminders = case.getValue("reminders").jsonArray.mapIndexed { index, element ->
                val reminder = element.jsonObject
                ReminderItem(
                    id = "r$index",
                    taskId = reminder.text("taskId")!!,
                    state = ReminderState.PENDING,
                    fireAt = LocalDateTime.parse(reminder.text("at")!!),
                )
            }

            val days = CalendarRules.build(tasks, reminders, case.day("from"), case.day("to"))
            val expect = case.getValue("expect").jsonObject
            assertEquals(name, expect.size, days.size)
            days.forEach { day ->
                val row = expect.getValue(day.day.toString()).jsonObject
                fun ids(field: String) = row.getValue(field).jsonArray.map { it.jsonPrimitive.content }
                assertEquals("$name ${day.day} planned", ids("planned"), day.planned.map(TaskItem::id))
                assertEquals("$name ${day.day} deadlines", ids("deadlines"), day.deadlines.map(TaskItem::id))
                assertEquals("$name ${day.day} repeats", ids("repeats"), day.repeats.map(TaskItem::id))
                assertEquals("$name ${day.day} reminders", row.getValue("reminders").jsonPrimitive.int, day.reminders)
            }
        }
    }
}
