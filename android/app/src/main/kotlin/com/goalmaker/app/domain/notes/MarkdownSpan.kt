package com.goalmaker.app.domain.notes

/** A run of text in a note with one style; [link] is the address when the run is a link. */
data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)
