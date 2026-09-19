package com.goalmaker.app.domain.notes

/** One line of a note: a list item when [bullet], otherwise plain text. An empty line has no spans. */
data class MarkdownBlock(val bullet: Boolean, val spans: List<MarkdownSpan>)
