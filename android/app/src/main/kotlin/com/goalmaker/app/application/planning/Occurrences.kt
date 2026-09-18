package com.goalmaker.app.application.planning

import java.security.MessageDigest
import java.util.Locale

/**
 * How a repeating task's series moves on across devices (docs/repeating.md,
 * contracts/vectors/recurrence.json): the ids the next occurrence and its tag links get, and which
 * open occurrences to drop when a sync left a series with more than one.
 */
object Occurrences {
    private val NAMESPACE = "77797aa79e1142d8a66724b0596d2a4f".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

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

    // A UUID version 5 (RFC 9562): SHA-1 of the namespace and the name, with the version and variant set.
    private fun nameBased(name: String): String {
        val hash = MessageDigest.getInstance("SHA-1").run {
            update(NAMESPACE)
            digest(name.toByteArray(Charsets.UTF_8))
        }
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = hash.take(16).joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
