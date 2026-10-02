package com.goalmaker.app.ui.settings

import com.goalmaker.app.domain.settings.SettingsFieldRules
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Settings text field saves on commit, and a bad value never replaces the last good one. */
class CommittedFieldTest {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val field = CommittedField("22:00", SettingsFieldRules::time, clock::format)

    @Test
    fun `a good time saves on commit, written the field's way`() {
        field.edit("7:30")
        assertEquals(LocalTime.of(7, 30), field.commit())
        assertEquals("07:30", field.text)
        assertEquals("07:30", field.lastGood)
        assertFalse(field.invalid)
    }

    @Test
    fun `a bad time is refused, never saved, and the last good value stays in effect`() {
        field.edit("25:00")
        assertNull(field.commit())
        assertTrue(field.invalid)
        assertEquals("22:00", field.lastGood)
        assertEquals("the owner's text stays to be fixed", "25:00", field.text)
    }

    @Test
    fun `typing does not check, and fixing the text clears the error at once`() {
        field.edit("2")
        assertFalse("no error while typing", field.invalid)
        assertNull(field.commit())
        assertTrue(field.invalid)
        field.edit("23:15")
        assertFalse(field.invalid)
        assertEquals(LocalTime.of(23, 15), field.commit())
    }

    @Test
    fun `committing the same value saves nothing`() {
        field.edit("22:00")
        assertNull(field.commit())
        assertFalse(field.invalid)
    }

    @Test
    fun `a value stored elsewhere shows unless the owner is typing`() {
        field.stored("00:00", editing = false)
        assertEquals("00:00", field.text)
        field.edit("21:")
        field.stored("06:00", editing = true)
        assertEquals("21:", field.text)
        assertEquals("06:00", field.lastGood)
    }
}
