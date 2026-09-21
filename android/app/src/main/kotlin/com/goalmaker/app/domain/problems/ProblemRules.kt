package com.goalmaker.app.domain.problems

import java.time.Instant

/**
 * What the owner is told has gone wrong, and where (docs/problems.md,
 * contracts/vectors/problems.json). A kind holds one problem at a time: a newer one takes the place of
 * the older, because what matters is what is wrong now rather than every time it has been.
 */
object ProblemRules {
    /** Changes that would not reach the server. */
    const val SYNC = "sync"

    /** The weekly export into the owner's folder. */
    const val BACKUP = "backup"

    /** An update that could not be found, verified or installed. */
    const val UPDATE = "update"

    /** Every kind both apps know, in no particular order. */
    val KINDS: List<String> = listOf(SYNC, BACKUP, UPDATE)

    /** That kind went wrong: its problem goes to the front, unread, and any older one goes. */
    fun report(problems: List<Problem>, kind: String, at: Instant, detail: String?): List<Problem> =
        listOf(Problem(kind, at, detail)) + problems.filterNot { it.kind == kind }

    /** That kind came right by itself, so there is nothing left to tell. */
    fun clear(problems: List<Problem>, kind: String): List<Problem> = problems.filterNot { it.kind == kind }

    /** The owner opened the problems: they stay, the mark goes. */
    fun read(problems: List<Problem>): List<Problem> = problems.map { it.copy(unread = false) }

    /** Whether anything is waiting to be read: the quiet mark on the Settings item. */
    fun isMarked(problems: List<Problem>): Boolean = problems.any { it.unread }
}
