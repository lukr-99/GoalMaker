package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.RitualReminder
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
    fun `every schedule`() {
        val cases = vectors.getValue("schedule").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val reminders = case.getValue("reminders").jsonArray.map { listed(it.jsonObject) }
            val tasks = case.getValue("tasks").jsonArray.map { listedTask(it.jsonObject) }.associateBy(TaskItem::id)
            val window = case.getValue("quietHours").let { value ->
                if (value == JsonNull) QuietHours.OFF else QuietHours(time(value.jsonObject.getValue("start"))!!, time(value.jsonObject.getValue("end"))!!)
            }
            val now = dateTime(case.getValue("now"))!!
            val due = ReminderSchedule.due(reminders, tasks, window, dateTime(case.getValue("since"))!!, now).map(ScheduledReminder::id)
            val next = ReminderSchedule.next(reminders, tasks, window, now)?.let { it.id to it.at }
            val expectedDue = case.getValue("due").jsonArray.map { it.jsonPrimitive.content }
            val expectedNext = case.getValue("next").let { value ->
                if (value == JsonNull) null else value.jsonObject.getValue("id").jsonPrimitive.content to dateTime(value.jsonObject.getValue("at"))
            }
            if (due == expectedDue && next == expectedNext) null else "${name(case)}: expected $expectedDue then $expectedNext, got $due then $next"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `every stale notification`() {
        val cases = vectors.getValue("stale").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val reminders = case.getValue("reminders").jsonArray.map { listed(it.jsonObject) }
            val tasks = case.getValue("tasks").jsonArray.map { listedTask(it.jsonObject) }.associateBy(TaskItem::id)
            val shown = case.getValue("shown").jsonArray.map { it.jsonPrimitive.content }
            val actual = ReminderSchedule.stale(shown, reminders, tasks, dateTime(case.getValue("now"))!!)
            val expected = case.getValue("clear").jsonArray.map { it.jsonPrimitive.content }
            if (actual == expected) null else "${name(case)}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `every evening ritual reminder`() {
        vectors.getValue("ritual").jsonArray.map { it.jsonObject }.forEach { case ->
            val time = case.getValue("time").let { if (it == JsonNull) null else LocalTime.parse(it.jsonPrimitive.content) }
            val start = case.getValue("dayStartHour").jsonPrimitive.int
            val ran = case.getValue("ran").jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }.toSet()
            val now = dateTime(case.getValue("now"))!!
            val due = RitualReminder.due(time, start, ran, dateTime(case.getValue("since"))!!, now)
            val next = RitualReminder.next(time, start, ran, now)
            assertEquals(name(case), date(case.getValue("due")) to dateTime(case.getValue("next")), due to next)
        }
    }

    @Test
    fun `every stale ritual reminder`() {
        vectors.getValue("ritualStale").jsonArray.map { it.jsonObject }.forEach { case ->
            val ran = case.getValue("ran").jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }.toSet()
            val stale = RitualReminder.stale(
                date(case.getValue("day"))!!,
                case.getValue("dayStartHour").jsonPrimitive.int,
                ran,
                dateTime(case.getValue("now"))!!,
            )
            assertEquals(name(case), case.getValue("stale").jsonPrimitive.boolean, stale)
        }
    }

    @Test
    fun `every ritual run id`() {
        vectors.getValue("ritualIds").jsonArray.map { it.jsonObject }.forEach { case ->
            assertEquals(
                case.getValue("id").jsonPrimitive.content,
                RitualRunList.idOf(case.getValue("owner").jsonPrimitive.content, case.getValue("ritual").jsonPrimitive.content, date(case.getValue("day"))!!),
            )
        }
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

    // A reminder in 'schedule' and 'stale', where fields that keep their defaults are left out.
    private fun listed(reminder: JsonObject) = ReminderItem(
        id = reminder.getValue("id").jsonPrimitive.content,
        taskId = reminder.getValue("task").jsonPrimitive.content,
        state = when (reminder["state"]?.jsonPrimitive?.content) {
            "snoozed" -> ReminderState.SNOOZED
            "dismissed" -> ReminderState.DISMISSED
            "done" -> ReminderState.DONE
            else -> ReminderState.PENDING
        },
        important = reminder["important"]?.jsonPrimitive?.boolean ?: false,
        fireAt = reminder["fireAt"]?.let(::dateTime),
        offsetMinutes = reminder["offsetMinutes"]?.jsonPrimitive?.int,
        snoozedUntil = reminder["snoozedUntil"]?.let(::dateTime),
    )

    private fun listedTask(task: JsonObject) = TaskItem(
        id = task.getValue("id").jsonPrimitive.content,
        title = "Take the bread out",
        state = when (task["status"]?.jsonPrimitive?.content) {
            "done" -> TaskState.DONE
            "dropped" -> TaskState.DROPPED
            else -> TaskState.OPEN
        },
        topPriority = false,
        createdAt = "2026-09-10T08:00:00.000000Z",
        plannedDate = task["planned"]?.let(::date),
        plannedTime = task["time"]?.let(::time),
    )

    private fun name(case: JsonObject) = case.getValue("name").jsonPrimitive.content

    private fun date(value: JsonElement) = if (value == JsonNull) null else LocalDate.parse(value.jsonPrimitive.content)

    private fun time(value: JsonElement) = if (value == JsonNull) null else LocalTime.parse(value.jsonPrimitive.content)

    private fun dateTime(value: JsonElement) = if (value == JsonNull) null else LocalDateTime.parse(value.jsonPrimitive.content)
}
