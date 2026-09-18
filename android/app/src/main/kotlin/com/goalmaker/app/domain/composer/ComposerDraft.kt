package com.goalmaker.app.domain.composer

import java.time.LocalDate
import java.time.LocalTime

/**
 * What one composer line says (docs/composer.md). [repeat] is an RRULE subset; [spans] lists every
 * recognized part so the composer can highlight it and a chip can remove its own text.
 */
data class ComposerDraft(
    val title: String,
    val plannedDate: LocalDate? = null,
    val plannedTime: LocalTime? = null,
    val tags: List<String> = emptyList(),
    val area: String? = null,
    val project: String? = null,
    val topPriority: Boolean = false,
    val idea: Boolean = false,
    val repeat: String? = null,
    val command: ComposerCommand? = null,
    val spans: List<ComposerSpan> = emptyList(),
)
