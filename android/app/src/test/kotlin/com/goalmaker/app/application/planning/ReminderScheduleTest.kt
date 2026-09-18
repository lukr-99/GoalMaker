package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a device shows now and what it arms next (docs/reminders.md). */
class ReminderScheduleTest {
    private val night = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0))

    @Test
    fun `sorts by time and settles ties by id`() {
        val schedule = ReminderSchedule.resolve(
            listOf(at("b", "2026-09-18T17:00"), at("a", "2026-09-18T17:00"), at("c", "2026-09-18T09:00")),
            tasks(),
            QuietHours.OFF,
        )
        assertEquals(listOf("c", "a", "b"), schedule.map(ScheduledReminder::id))
    }

    @Test
    fun `carries the task's title to the notification`() {
        val schedule = ReminderSchedule.resolve(listOf(at("a", "2026-09-18T17:00")), tasks(), QuietHours.OFF)
        assertEquals("Take the bread out", schedule.single().taskTitle)
    }

    @Test
    fun `a reminder whose task is gone is not scheduled`() {
        val orphan = at("a", "2026-09-18T17:00").copy(taskId = "missing")
        assertEquals(emptyList<ScheduledReminder>(), ReminderSchedule.resolve(listOf(orphan), tasks(), QuietHours.OFF))
    }

    @Test
    fun `due covers the moment itself and everything missed`() {
        val all = listOf(at("a", "2026-09-18T08:00"), at("b", "2026-09-18T09:00"), at("c", "2026-09-18T10:00"))
        val due = ReminderSchedule.due(all, tasks(), QuietHours.OFF, LocalDateTime.parse("2026-09-18T09:00"))
        assertEquals(listOf("a", "b"), due.map(ScheduledReminder::id))
    }

    @Test
    fun `next is the first one still ahead`() {
        val all = listOf(at("a", "2026-09-18T08:00"), at("b", "2026-09-18T09:00"), at("c", "2026-09-18T10:00"))
        val next = ReminderSchedule.next(all, tasks(), QuietHours.OFF, LocalDateTime.parse("2026-09-18T09:00"))
        assertEquals("c", next?.id)
    }

    @Test
    fun `nothing ahead arms nothing`() {
        val all = listOf(at("a", "2026-09-18T08:00"))
        assertNull(ReminderSchedule.next(all, tasks(), QuietHours.OFF, LocalDateTime.parse("2026-09-18T09:00")))
    }

    @Test
    fun `quiet hours move an ordinary reminder out of the night`() {
        val all = listOf(at("a", "2026-09-18T23:30"))
        val next = ReminderSchedule.next(all, tasks(), night, LocalDateTime.parse("2026-09-18T20:00"))
        assertEquals(LocalDateTime.parse("2026-09-19T07:00"), next?.at)
    }

    @Test
    fun `an important reminder keeps its place in the night`() {
        val all = listOf(at("a", "2026-09-18T23:30").copy(important = true))
        val next = ReminderSchedule.next(all, tasks(), night, LocalDateTime.parse("2026-09-18T20:00"))
        assertEquals(LocalDateTime.parse("2026-09-18T23:30"), next?.at)
    }

    @Test
    fun `holding a reminder back can change which one comes first`() {
        val all = listOf(at("night", "2026-09-18T23:30"), at("morning", "2026-09-19T06:00").copy(important = true))
        val order = ReminderSchedule.resolve(all, tasks(), night).map(ScheduledReminder::id)
        assertEquals(listOf("morning", "night"), order)
    }

    private fun at(id: String, fireAt: String) = ReminderItem(
        id = id,
        taskId = TASK,
        state = ReminderState.PENDING,
        fireAt = LocalDateTime.parse(fireAt),
    )

    private fun tasks() = mapOf(
        TASK to TaskItem(
            id = TASK,
            title = "Take the bread out",
            state = TaskState.OPEN,
            topPriority = false,
            createdAt = "2026-09-10T08:00:00.000000Z",
            plannedDate = LocalDate.parse("2026-09-18"),
            plannedTime = LocalTime.parse("17:00"),
        ),
    )

    private companion object {
        const val TASK = "66666666-7777-4888-8999-aaaaaaaaaaaa"
    }
}
