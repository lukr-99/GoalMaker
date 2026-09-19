package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.NameBasedUuid
import java.util.Locale

/**
 * How a repeating task's series moves on across devices (docs/repeating.md,
 * contracts/vectors/recurrence.json): the ids the next occurrence and its tag links get, and which
 * open occurrences to drop when a sync left a series with more than one.
 */
object Occurrences {
    private const val NAMESPACE = "77797aa7-9e11-42d8-a667-24b0596d2a4f"

    /** The next occurrence's id: the same on every device that moves this occurrence on. */
    fun successorId(id: String): String = nameBased(id.lowercase(Locale.ROOT))

    /** The id of a next occurrence's link to a tag. */
    fun tagLinkId(taskId: String, tagId: String): String =
        nameBased("${taskId.lowercase(Locale.ROOT)}/${tagId.lowercase(Locale.ROOT)}")

    /** The series a task belongs to: its series_id, or its own id when it has none. */
    fun seriesOf(task: TaskItem): String = task.seriesId ?: task.id

    /** The open occurrences to drop so each series keeps one: the one planned latest (ties: the larger id). */
    fun toDrop(tasks: List<TaskItem>): List<String> = tasks
        .filter { !it.deleted && it.state == TaskState.OPEN }
        .groupBy(::seriesOf)
        .values
        .filter { it.size > 1 }
        .flatMap { series ->
            series.sortedWith(compareByDescending<TaskItem, java.time.LocalDate?>(nullsFirst()) { it.plannedDate }.thenByDescending { it.id })
                .drop(1)
        }
        .map(TaskItem::id)

    private fun nameBased(name: String): String = NameBasedUuid.of(NAMESPACE, name)
}
