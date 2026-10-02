package com.goalmaker.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which controls just saved, for the "Saved" mark next to each. Every save counts up its control's
 * number, so the mark plays again for a second change while the first is still showing, and only
 * next to the control that changed.
 */
class SavedMarks {
    private val marks = MutableStateFlow<Map<SettingKey, Int>>(emptyMap())

    /** Each control's save count; a control that never saved is missing. */
    val counts: StateFlow<Map<SettingKey, Int>> = marks.asStateFlow()

    /** [key]'s change was kept. */
    fun mark(key: SettingKey) = marks.update { it + (key to (it[key] ?: 0) + 1) }
}
