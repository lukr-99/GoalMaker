package com.goalmaker.app.ui.composer

/**
 * What sending a line on Wants, Habits or Goals did: the item was [Added], or the line can't be added
 * as it stands (a want without its reason, a habit or goal with no name), so the form opens with
 * [OpenForm.prefill], what was read so far.
 */
sealed interface LineOutcome<out T> {
    data object Added : LineOutcome<Nothing>

    data class OpenForm<T>(val prefill: T) : LineOutcome<T>
}
