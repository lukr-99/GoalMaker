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
                    areaId = task.text("area"),
                    projectId = task.text("project"),
                )
            }
            val links = case.getValue("tasks").jsonArray.associate { element ->
                val task = element.jsonObject
                task.text("id")!! to task["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
            }
            val projectAreas = case["projects"]?.jsonArray.orEmpty().associate { element ->
                element.jsonObject.text("id")!! to element.jsonObject.text("area")
            }
            val filter = ListFilter(areaId = case.text("area"), tagId = case.text("tag"))
            val reminders = case.getValue("reminders").jsonArray.mapIndexed { index, element ->
                val reminder = element.jsonObject
                ReminderItem(
                    id = "r$index",
                    taskId = reminder.text("taskId")!!,
                    state = ReminderState.PENDING,
                    fireAt = LocalDateTime.parse(reminder.text("at")!!),
                )
            }

            val days = CalendarRules.build(tasks, reminders, case.day("from"), case.day("to")) { filter.keeps(it, links, projectAreas) }
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

    private fun events(case: JsonObject): List<EventItem> = case.getValue("events").jsonArray.map { element ->
        val event = element.jsonObject
        EventItem(
            id = event.text("id")!!,
            title = event.text("title")!!,
            startsOn = event.day("startsOn"),
            endsOn = event.day("endsOn"),
            areaId = event.text("area"),
            deleted = event["deleted"]?.jsonPrimitive?.boolean ?: false,
        )
    }

    @Test
    fun `every day's events`() {
        vectors.cases("eventDays").forEach { case ->
            val name = case.text("name")
            val filter = ListFilter(areaId = case.text("area"), tagId = case.text("tag"))
            val days = EventRules.days(events(case), case.day("from"), case.day("to"), filter::keeps)
            val expect = case.getValue("expect").jsonObject
            assertEquals(name, expect.keys.map(LocalDate::parse), days.keys.toList())
            days.forEach { (day, held) ->
                val ids = expect.getValue(day.toString()).jsonArray.map { it.jsonPrimitive.content }
                assertEquals("$name $day", ids, held.map(EventItem::id))
                assertEquals("$name $day on its own", ids, EventRules.onDay(events(case), day, filter::keeps).map(EventItem::id))
            }
        }
    }

    @Test
    fun `every grid's bars`() {
        vectors.cases("bars").forEach { case ->
            val name = case.text("name")
            val rows = EventRules.bars(events(case), case.day("start"), case.day("end"))
            val expect = case.getValue("rows").jsonArray.map { row ->
                row.jsonArray.map { element ->
                    val bar = element.jsonObject
                    listOf(
                        bar.text("id"),
                        bar.getValue("from").jsonPrimitive.int,
                        bar.getValue("to").jsonPrimitive.int,
                        bar.getValue("lane").jsonPrimitive.int,
                        bar.getValue("before").jsonPrimitive.boolean,
                        bar.getValue("after").jsonPrimitive.boolean,
                    )
                }
            }
            val actual = rows.map { row -> row.map { listOf(it.event.id, it.from, it.to, it.lane, it.before, it.after) } }
            assertEquals(name, expect, actual)
        }
    }

    @Test
    fun `every ongoing event`() {
        vectors.cases("ongoing").forEach { case ->
            val expect = case.getValue("expect").jsonArray.map { element ->
                val on = element.jsonObject
                Triple(on.text("id"), on.getValue("dayOf").jsonPrimitive.int, on.getValue("days").jsonPrimitive.int)
            }
            val actual = EventRules.ongoing(events(case), case.day("day")).map { Triple(it.event.id, it.dayOf, it.days) }
            assertEquals(case.text("name"), expect, actual)
        }
    }
}
