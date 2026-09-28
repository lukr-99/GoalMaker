package com.goalmaker.app.domain.notes

/**
 * One line of a note: a list item when [bullet], a heading of level 1 to 3 when [heading] is above 0,
 * otherwise plain text. An empty line has no spans.
 */
data class MarkdownBlock(val bullet: Boolean, val spans: List<MarkdownSpan>, val heading: Int = 0)
