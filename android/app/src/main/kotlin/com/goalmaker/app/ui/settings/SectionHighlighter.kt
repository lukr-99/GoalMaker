package com.goalmaker.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goalmaker.app.domain.settings.HintKind
import com.goalmaker.app.domain.settings.SettingsPageRules

/**
 * Which card lights up, and when (docs/design/spec.md, Settings). A jump (a chip, or a deep link
 * such as the update notification) pins its target as the current section, and once the scroll
 * lands the card gets the jump hint; if the owner scrolls before it lands, the jump stops and no
 * hint plays. Scrolling into a section by hand gives the quieter scroll hint once the page is
 * still. The screen tells this what happens; the timing comes from [SettingsPageRules].
 */
class SectionHighlighter<T>(private val reduceMotion: () -> Boolean) {
    /** The section a jump went to: current while the page scrolls there and after it lands, until the owner scrolls. */
    var pinned by mutableStateOf<T?>(null)
        private set

    /** The hint the cards are playing, newest first; a card plays it when the section is its own. */
    var hint by mutableStateOf<HintRequest<T>?>(null)
        private set

    /** Whether the page is scrolling for a jump, so the owner's own scrolling can be told apart. */
    var jumping = false
        private set

    private var serial = 0
    private var lastHinted: T? = null
    private var scrolledByHand = false

    /** The page opened on [current], which the owner is already reading, so it needs no hint. */
    fun opened(current: T?) {
        if (lastHinted == null) lastHinted = current
    }

    /** A jump to [target] starts scrolling. */
    fun jumpStarted(target: T) {
        pinned = target
        jumping = true
        lastHinted = target
    }

    /** The jump's scroll reached [target]: the card lights up. */
    fun jumpLanded(target: T) {
        if (pinned != target || !jumping) return
        jumping = false
        scrolledByHand = false
        play(target, HintKind.JUMP)
    }

    /** The owner scrolled while the page was scrolling to [target]: the jump stops, and no hint plays. */
    fun jumpCancelled(target: T) {
        if (pinned != target) return
        jumping = false
        pinned = null
    }

    /** The owner scrolls the page by hand: a landed jump lets go of its section. */
    fun scrolledByHand() {
        if (jumping) return
        pinned = null
        scrolledByHand = true
    }

    /** The page has been still for a moment with [current] the section being read. */
    fun settled(current: T?) {
        if (!scrolledByHand) return
        scrolledByHand = false
        if (!SettingsPageRules.playsScrollHint(current, lastHinted, jumping, reduceMotion())) return
        lastHinted = current
        play(current ?: return, HintKind.SCROLL)
    }

    private fun play(section: T, kind: HintKind) {
        val plan = SettingsPageRules.hint(kind, reduceMotion()) ?: return
        hint = HintRequest(section, kind, plan, ++serial)
    }
}
