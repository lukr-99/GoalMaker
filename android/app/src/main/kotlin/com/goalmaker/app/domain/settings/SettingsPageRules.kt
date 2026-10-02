package com.goalmaker.app.domain.settings

/**
 * The Settings page's jump navigation and its hints, shared with the Windows app through
 * contracts/vectors/settings.json. Sections are whatever the app names them by, so the same rules
 * run over the phone's enum and the contract's ids. Distances are in dp on the phone.
 */
object SettingsPageRules {
    /** The chip row only shows with this many visible sections or more. */
    const val MINIMUM_SECTIONS = 4

    /** The line below the top of the scroll area that a section's top has to reach to be current. */
    const val CURRENT_LINE = 80

    /** How close to the bottom counts as the bottom. */
    const val BOTTOM_SLACK = 2

    /** How long the page has to be still before the scroll hint plays. */
    const val SCROLL_HINT_IDLE_MILLIS = 150L

    /** The Saved mark next to a control: in, hold, out. */
    const val SAVED_IN_MILLIS = 120
    const val SAVED_HOLD_MILLIS = 1_500L
    const val SAVED_OUT_MILLIS = 250

    private const val JUMP_SHORTEST = 250
    private const val JUMP_LONGEST = 450

    private val jump = SectionHint(150, 350, 700, tint = true, ring = true, glow = true, edge = 1f, title = true)
    private val jumpReduced = SectionHint(0, 900, 0, tint = true, ring = true, glow = false, edge = 1f, title = true)
    private val scroll = SectionHint(120, 0, 580, tint = false, ring = false, glow = false, edge = 0.85f, title = true)

    /** Whether the page shows the chip row at all; with fewer sections it is short enough to read. */
    fun showNavigation(sections: Int): Boolean = sections >= MINIMUM_SECTIONS

    /**
     * The section being read: the [pinned] one while a jump goes there and after it lands; at the
     * bottom of a page that scrolls, the last one; otherwise the last whose top is at or above the
     * line [line] below the top of the scroll area. Sections without a top in [tops] are skipped.
     */
    fun <T> current(
        sections: List<T>,
        tops: Map<T, Int>,
        scroll: Int,
        maxScroll: Int,
        pinned: T? = null,
        line: Int = CURRENT_LINE,
        bottomSlack: Int = BOTTOM_SLACK,
    ): T? {
        if (pinned != null && pinned in sections) return pinned
        val measured = sections.filter { it in tops }
        if (measured.isEmpty()) return sections.firstOrNull()
        if (maxScroll > 0 && scroll >= maxScroll - bottomSlack) return measured.last()
        return measured.lastOrNull { tops.getValue(it) <= scroll + line } ?: measured.first()
    }

    /** How long the jump's scroll takes over [distance]: 250 to 450 ms, instant when tiny or with reduce motion. */
    fun jumpScrollMillis(distance: Int, reduceMotion: Boolean): Int {
        val far = kotlin.math.abs(distance)
        if (reduceMotion || far < 2) return 0
        return minOf(JUMP_LONGEST, JUMP_SHORTEST + (far * 12 + 50) / 100)
    }

    /** What the card does for [kind], or null when it does nothing. */
    fun hint(kind: HintKind, reduceMotion: Boolean): SectionHint? = when (kind) {
        HintKind.JUMP -> if (reduceMotion) jumpReduced else jump
        HintKind.SCROLL -> if (reduceMotion) null else scroll
    }

    /** Whether the scroll hint plays for [current] once the page has been still for a moment. */
    fun <T> playsScrollHint(current: T?, lastHinted: T?, jumping: Boolean, reduceMotion: Boolean): Boolean =
        current != null && current != lastHinted && !jumping && !reduceMotion
}
