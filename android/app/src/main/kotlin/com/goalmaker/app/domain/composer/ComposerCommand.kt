package com.goalmaker.app.domain.composer

/** A `/command` line: its lowercased name, the rest of the line, and whether the app knows it. */
data class ComposerCommand(
    val name: String,
    val argument: String,
    val known: Boolean,
)
