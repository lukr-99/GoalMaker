package com.goalmaker.app.domain.settings

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Settings text fields accept. */
class SettingsFieldRulesTest {
    @Test
    fun `times are hours and minutes`() {
        assertEquals(LocalTime.of(22, 0), SettingsFieldRules.time("22:00"))
        assertEquals(LocalTime.of(7, 30), SettingsFieldRules.time(" 7:30 "))
        assertEquals(LocalTime.MIDNIGHT, SettingsFieldRules.time("00:00"))
        assertNull(SettingsFieldRules.time("24:00"))
        assertNull(SettingsFieldRules.time("22:60"))
        assertNull(SettingsFieldRules.time("22"))
        assertNull(SettingsFieldRules.time("10 pm"))
        assertNull(SettingsFieldRules.time(""))
    }

    @Test
    fun `a backend is an http or https address with a host`() {
        assertTrue(SettingsFieldRules.backendUrl("http://192.168.1.20:55321"))
        assertTrue(SettingsFieldRules.backendUrl("https://abc.supabase.co"))
        assertTrue(SettingsFieldRules.backendUrl("http://127.0.0.1:55321/"))
        assertFalse(SettingsFieldRules.backendUrl("192.168.1.20:55321"))
        assertFalse(SettingsFieldRules.backendUrl("ftp://example.com"))
        assertFalse(SettingsFieldRules.backendUrl("http://"))
        assertFalse(SettingsFieldRules.backendUrl("http://exa mple.com"))
        assertFalse(SettingsFieldRules.backendUrl(""))
    }
}
