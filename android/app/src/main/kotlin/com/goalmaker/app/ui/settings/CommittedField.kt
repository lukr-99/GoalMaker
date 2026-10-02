package com.goalmaker.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A Settings text field that saves on Enter or when focus leaves (docs/design/spec.md, Settings).
 * [accept] turns the text into a value, or null when it is not one, and [format] writes a value the
 * way the field shows it, so 7:30 and 07:30 are the same time. A bad value is never saved: the
 * field says so and the last good value stays in effect. The check runs on commit, not on every
 * key, but typing a fix clears the error at once.
 */
class CommittedField<V>(good: String, private val accept: (String) -> V?, private val format: (V) -> String) {
    /** What the field shows. */
    var text by mutableStateOf(good)
        private set

    /** The last text that was saved, or the stored value it started from. */
    var lastGood by mutableStateOf(good)
        private set

    /** Whether the last commit was refused; cleared as soon as the text is good again. */
    var invalid by mutableStateOf(false)
        private set

    /** The owner typed. */
    fun edit(value: String) {
        text = value
        if (invalid && accept(value) != null) invalid = false
    }

    /**
     * Enter, or focus left: the value to save, or null when there is nothing to save, either because
     * the text did not change or because it is refused (then [invalid] is set).
     */
    fun commit(): V? {
        val value = accept(text)
        if (value == null) {
            invalid = true
            return null
        }
        invalid = false
        val written = format(value)
        text = written
        if (written == lastGood) return null
        lastGood = written
        return value
    }

    /** The stored value changed elsewhere (for example Switch off): show it, unless the owner is mid-edit. */
    fun stored(value: String, editing: Boolean) {
        if (value == lastGood) return
        lastGood = value
        if (!editing) {
            text = value
            invalid = false
        }
    }
}
