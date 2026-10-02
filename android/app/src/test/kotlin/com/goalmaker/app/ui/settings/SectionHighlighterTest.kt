package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest
import com.goalmaker.app.domain.settings.HintKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When a Settings card lights up: after a jump lands, after a hand scroll settles, and never when it shouldn't. */
class SectionHighlighterTest {
    private var reduced = false
    private val highlighter = SectionHighlighter<SettingsSection> { reduced }

    @Test
    fun `the update notification jumps to Updates and plays the jump hint once it lands`() {
        val target = SettingsSections.target(UpdateRequest.SHOW)!!
        highlighter.opened(SettingsSection.ACCOUNT)
        highlighter.jumpStarted(target)
        assertEquals(SettingsSection.UPDATES, highlighter.pinned)
        assertNull("nothing lights up while the page is still scrolling", highlighter.hint)

        highlighter.jumpLanded(target)

        val hint = highlighter.hint!!
        assertEquals(SettingsSection.UPDATES, hint.section)
        assertEquals(HintKind.JUMP, hint.kind)
        assertEquals(150, hint.plan.riseMillis)
        assertEquals(350, hint.plan.holdMillis)
        assertEquals(700, hint.plan.fadeMillis)
        assertEquals(true, hint.plan.glow)
    }

    @Test
    fun `with reduce motion the deep link still lands with a static highlight`() {
        reduced = true
        highlighter.jumpStarted(SettingsSection.UPDATES)
        highlighter.jumpLanded(SettingsSection.UPDATES)
        val plan = highlighter.hint!!.plan
        assertEquals(0, plan.riseMillis)
        assertEquals(900, plan.holdMillis)
        assertEquals(0, plan.fadeMillis)
        assertEquals(false, plan.glow)
    }

    @Test
    fun `a chip's target stays current after landing until the owner scrolls`() {
        highlighter.jumpStarted(SettingsSection.CLAUDE)
        highlighter.jumpLanded(SettingsSection.CLAUDE)
        assertEquals(SettingsSection.CLAUDE, highlighter.pinned)
        highlighter.scrolledByHand()
        assertNull(highlighter.pinned)
    }

    @Test
    fun `the jump's own scrolling does not let go of the target`() {
        highlighter.jumpStarted(SettingsSection.CLAUDE)
        highlighter.scrolledByHand()
        assertEquals(SettingsSection.CLAUDE, highlighter.pinned)
    }

    @Test
    fun `a finger on the page during the jump stops it, and no hint plays`() {
        highlighter.jumpStarted(SettingsSection.ABOUT)
        highlighter.jumpCancelled(SettingsSection.ABOUT)
        highlighter.jumpLanded(SettingsSection.ABOUT)
        assertNull(highlighter.pinned)
        assertNull(highlighter.hint)
    }

    @Test
    fun `a second jump is not undone when the first one stops`() {
        highlighter.jumpStarted(SettingsSection.ABOUT)
        highlighter.jumpStarted(SettingsSection.PLANNING)
        highlighter.jumpCancelled(SettingsSection.ABOUT)
        highlighter.jumpLanded(SettingsSection.PLANNING)
        assertEquals(SettingsSection.PLANNING, highlighter.hint!!.section)
    }

    @Test
    fun `scrolling into a new section by hand plays the scroll hint once the page is still`() {
        highlighter.opened(SettingsSection.ACCOUNT)
        highlighter.scrolledByHand()
        highlighter.settled(SettingsSection.PLANNING)
        val hint = highlighter.hint!!
        assertEquals(SettingsSection.PLANNING, hint.section)
        assertEquals(HintKind.SCROLL, hint.kind)
        assertEquals(0.85f, hint.plan.edge)
        assertEquals(false, hint.plan.tint)
    }

    @Test
    fun `the section already being read does not flash`() {
        highlighter.opened(SettingsSection.ACCOUNT)
        highlighter.scrolledByHand()
        highlighter.settled(SettingsSection.ACCOUNT)
        assertNull(highlighter.hint)
    }

    @Test
    fun `settling without a hand scroll, like after a jump, plays no scroll hint`() {
        highlighter.opened(SettingsSection.ACCOUNT)
        highlighter.settled(SettingsSection.PLANNING)
        assertNull(highlighter.hint)
    }

    @Test
    fun `with reduce motion scrolling plays no hint`() {
        reduced = true
        highlighter.opened(SettingsSection.ACCOUNT)
        highlighter.scrolledByHand()
        highlighter.settled(SettingsSection.PLANNING)
        assertNull(highlighter.hint)
    }

    @Test
    fun `the same jump twice plays twice`() {
        highlighter.jumpStarted(SettingsSection.UPDATES)
        highlighter.jumpLanded(SettingsSection.UPDATES)
        val first = highlighter.hint!!.serial
        highlighter.jumpStarted(SettingsSection.UPDATES)
        highlighter.jumpLanded(SettingsSection.UPDATES)
        assertEquals(first + 1, highlighter.hint!!.serial)
    }
}
