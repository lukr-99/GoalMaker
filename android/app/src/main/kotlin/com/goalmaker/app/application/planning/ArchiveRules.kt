package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.sync.SyncRules
import java.text.Normalizer
import java.util.Locale

/** The archive of done tasks (docs/archive.md, contracts/vectors/archive.json). */
object ArchiveRules {
    private val marks = Regex("\\p{M}+")

    /**
     * The done tasks that match [query], the most recently completed first (ties by id). Every word
     * of the query has to be found in the title or the notes, ignoring case and accents.
     */
    fun search(tasks: List<TaskItem>, query: String): List<TaskItem> {
        val words = query.split(' ').map(::fold).filter(String::isNotEmpty)
        return tasks
            .filter { !it.deleted && it.state == TaskState.DONE }
            .filter { task ->
                val text = fold(task.title) + "\n" + fold(task.notes)
                words.all { it in text }
            }
            .sortedWith(
                compareByDescending<TaskItem> { task -> task.completedAt?.let(SyncRules::instantOf) }.thenBy(TaskItem::id),
            )
    }

    /** Text as the search compares it: without accents, in lower case. */
    fun fold(text: String): String = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
}
