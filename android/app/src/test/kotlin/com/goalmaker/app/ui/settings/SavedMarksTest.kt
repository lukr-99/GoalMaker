package com.goalmaker.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Saved mark: it shows next to the control that changed, and again for each new change. */
class SavedMarksTest {
    private val marks = SavedMarks()

    @Test
    fun `nothing is marked before a change`() {
        assertEquals(emptyMap<SettingKey, Int>(), marks.counts.value)
    }

    @Test
    fun `a change marks only its own control`() {
        marks.mark(SettingKey.PURE_BLACK)
        assertEquals(1, marks.counts.value[SettingKey.PURE_BLACK])
        assertNull(marks.counts.value[SettingKey.MODE])
    }

    @Test
    fun `a second change plays the mark again`() {
        marks.mark(SettingKey.QUIET_START)
        marks.mark(SettingKey.QUIET_START)
        marks.mark(SettingKey.QUIET_END)
        assertEquals(2, marks.counts.value[SettingKey.QUIET_START])
        assertEquals(1, marks.counts.value[SettingKey.QUIET_END])
    }
}
