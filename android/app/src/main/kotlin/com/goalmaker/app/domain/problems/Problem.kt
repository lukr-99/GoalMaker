package com.goalmaker.app.domain.problems

import java.time.Instant

/**
 * Something that went wrong while nobody was watching (docs/problems.md): which [kind] of thing it
 * was, [at] when, what to keep behind "what happened" as [detail] for a bug report, and whether the
 * owner has seen it yet.
 */
data class Problem(
    val kind: String,
    val at: Instant,
    val detail: String? = null,
    val unread: Boolean = true,
)
