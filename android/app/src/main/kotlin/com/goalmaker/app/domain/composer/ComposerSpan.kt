package com.goalmaker.app.domain.composer

/** A recognized part of the line: [start] to [end] (exclusive) in UTF-16 code units. */
data class ComposerSpan(
    val kind: SpanKind,
    val start: Int,
    val end: Int,
)
