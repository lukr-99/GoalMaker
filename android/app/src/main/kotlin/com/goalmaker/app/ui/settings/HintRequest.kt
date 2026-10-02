package com.goalmaker.app.ui.settings

import com.goalmaker.app.domain.settings.HintKind
import com.goalmaker.app.domain.settings.SectionHint

/** One hint for one card: which section, why, what it does, and a [serial] so the same hint can play again. */
data class HintRequest<T>(
    val section: T,
    val kind: HintKind,
    val plan: SectionHint,
    val serial: Int,
)
