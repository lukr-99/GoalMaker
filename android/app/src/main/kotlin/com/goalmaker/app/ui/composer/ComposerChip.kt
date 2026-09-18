package com.goalmaker.app.ui.composer

import com.goalmaker.app.domain.composer.ComposerSpan
import com.goalmaker.app.domain.composer.SpanKind

/**
 * One part of the composer's preview: what the line will save. [note] says when it isn't plain
 * ("new" area or tag, "later" for parts a future milestone saves). [spans] is the text a tap removes.
 */
data class ComposerChip(
    val kind: SpanKind,
    val label: String,
    val note: String?,
    val muted: Boolean,
    val areaColorId: String?,
    val spans: List<ComposerSpan>,
)
