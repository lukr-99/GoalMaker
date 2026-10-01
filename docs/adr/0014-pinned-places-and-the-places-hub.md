# ADR 0014: Pinned places, with a Places hub on the phone

The phone's bottom bar (Today, Tomorrow, Inbox, Projects, Calendar) and its top bar (sync, Habits,
Goals, a menu with Reviews, Stats and Archive, Plan tomorrow, Settings) were both full, the Windows
sidebar listed eleven places, and M8 adds Wants and Tally. A prototype
(branch `prototype/m8-navigation`, `docs/design/prototypes/navigation-prototype.html`) compared a
Places hub of live tiles (A), four sections with chips and a grouped sidebar (B), and pinned places
with an All sheet and Ctrl+K (C). On 2026-09-28 the owner chose C on Windows and C on the phone with
A's hub in place of the sheet. **Phone:** the bottom bar holds four places the owner pins and a fifth
tab, Places: a page of live tiles for every place (rings, counts, the Tally bar, the Letter), pinned
ones marked, with an Edit mode where tiles toggle their pin. A fifth pin is refused until one is
unpinned, and the last pin stays; the Places tab counts what waits in places that are not pinned.
**Windows:** a Pinned group at the top of the sidebar (no limit, pinned from the title bar or a right click), All
places below it, Settings at the bottom, and Ctrl+K to jump to any place by typing. Pins are a device
setting, like the theme, so the phone and the PC keep their own. Sections (B) scaled well in the
sidebar but made every phone place two taps deep; a menu or sheet hides exactly the places worth
noticing, which is why the hub shows live numbers instead of names.

**Note, 2026-10-01.** The owner asked for the phone's hub on the PC too. All places now opens a
Places page with the same live tiles and Edit (PC pin rules: no limit, the last pin stays); the
arrow at its side only folds its list. The pins and Ctrl+K are unchanged.
