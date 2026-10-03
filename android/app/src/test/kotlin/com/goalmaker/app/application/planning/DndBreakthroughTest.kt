package com.goalmaker.app.application.planning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where important reminders stand against Do Not Disturb, and which system page changes it (docs/reminders.md). */
class DndBreakthroughTest {
    @Test
    fun `a channel that overrides Do Not Disturb lets them through`() {
        assertEquals(DndBreakthrough.ALLOWED, DndBreakthrough.of(notificationsOn = true, channelOn = true, bypassesDnd = true))
    }

    @Test
    fun `a channel that does not override it leaves them silenced`() {
        assertEquals(DndBreakthrough.NOT_ALLOWED, DndBreakthrough.of(notificationsOn = true, channelOn = true, bypassesDnd = false))
    }

    @Test
    fun `a channel switched off says so, whatever its override`() {
        assertEquals(DndBreakthrough.CHANNEL_OFF, DndBreakthrough.of(notificationsOn = true, channelOn = false, bypassesDnd = true))
    }

    @Test
    fun `notifications off come first, and only then the app's page opens`() {
        val off = DndBreakthrough.of(notificationsOn = false, channelOn = true, bypassesDnd = true)
        assertEquals(DndBreakthrough.NOTIFICATIONS_OFF, off)
        assertTrue(off.opensAppPage)
        assertFalse(DndBreakthrough.NOT_ALLOWED.opensAppPage)
        assertFalse(DndBreakthrough.CHANNEL_OFF.opensAppPage)
    }
}
