# GoalMaker design spec

How GoalMaker looks and moves on both apps. The decisions come from the
[design questionnaire](questionnaire.md) and [ADR 0008](../adr/0008-switchable-themes.md); the exact
values live in [`contracts/design/themes.json`](../../contracts/design/themes.json), which both apps
load and `tools/check_design_tokens.py` checks. This page explains the system; the JSON is the
source of truth for numbers and colors.

## Principles

- **Punchy, focused, rewarding.** Big type and numbers where progress lives, calm lists where work
  lives, a small celebration when something gets done.
- **One layout, four looks.** Themes change colors, fonts, corners and the style of big numbers.
  They never move things around or change behavior.
- **Readable first.** Every text and control pair meets WCAG AA in every theme and mode; layouts
  follow the system text size and are tested at the largest setting. What each screen does for a
  screen reader, the keyboard and large text is in [accessibility](accessibility.md).

## Themes

| Theme | Colors | Type | Corners |
|---|---|---|---|
| **Track** (default) | Black, white, volt `#D6FF3A` | Archivo: black-weight, wide, italic, uppercase headings | Sharp |
| Electric | Violet `#5B2EE6`, coral | Plus Jakarta Sans, extra-bold headings | Very round |
| Night | Indigo, cyan | Space Grotesk, bold headings | Medium |
| Sunrise | Tangerine, magenta | Outfit, extra-bold headings | Very round, blobby |

Each theme has a **light** and a **dark** palette. **Pure black** is a separate switch that keeps the
dark palette but puts it on true black surfaces (OLED); it applies to any theme. The mode follows
the system unless the user picks light or dark.

### Color roles

Screens use roles, never raw colors: `background`, `surface` (cards, rows), `surfaceVariant`
(composer, inputs), `text`, `textMuted`, `outline`, `primary`/`onPrimary` (buttons, send),
`accent`/`onAccent` (checks, progress, rings, section labels, task times and field labels),
`hero`/`onHero`/`heroAccent` (big-number cards), `danger`. Material 3 on Android and WPF UI on
Windows get their own color slots filled from these roles, so built-in controls match.

The accent is each theme's second color (coral in Electric, cyan in Night, magenta in Sunrise), so it
also carries every screen's headline, labels and times: the owner found it too rare when it only
marked checks (2026-09-19).
Because it is text there, it has to reach 4.5:1 like any text, which `tools/check_design_tokens.py`
checks.

In Track light, `accent` is black (volt on white is unreadable); volt appears on black: the hero
card, the send button's icon, checked boxes' marks.

### Area colors

Twenty colors that work in every theme: violet, blue, cyan, teal, green, lime, yellow, orange, red,
pink, magenta, slate, then indigo, sky, emerald, amber, coral, rose, purple and stone. Each has a
`swatch` (dots, the picker) and a chip pair (`container`, `content`) for light and dark, with chip
text at 4.5:1 or better. A new area takes the first color no area uses yet, which is why the first
twelve are spread around the color wheel. Color is never the only signal: chips always show the
area's name (and emoji, if any).

The owner chose more palette colors over a custom color picker (2026-09-19): every color stays
designed and checked for light and dark, and `areas.color` stays a palette id.

## Type

Each theme names three styles: `heading` (screen titles, section heroes), `body` (everything you
read) and `number` (big numbers, times, counts). Numbers always use tabular figures so they don't
jiggle as they change.

| Use | Size (phone / Windows) | Style |
|---|---|---|
| Screen title ("Today") | 34 / 28 | heading |
| Big number (goal 3/5, streak) | 44 to 64 / 40 to 56 | number, on hero cards and stats only |
| Card title | 17 / 15 | body, strong weight |
| Body, task titles | 15 / 14 | body |
| Secondary, dates, counts | 13 / 12 | body, `textMuted` |
| Section label | 11 / 11 | body, strong, uppercase, letter-spaced |

Track's headings are uppercase and italic; the other themes use sentence case upright. Fonts are
bundled (SIL Open Font License, texts kept in the repository); nothing loads from the network.

## Shape and density

Corner radii come from the theme (`shape.card`, `row`, `checkbox`, `button`; 999 means fully
round). The composer is always a pill. Spacing comes from the device: the phone is **airy** (rows
at least 52 dp, 8 dp between rows, 16 dp page padding); Windows is **compact** (rows 36 px, 4 px
gaps). Touch targets on the phone stay at least 48 dp regardless of theme.

## Motion and feedback

Level 3 of 5 on the celebration scale:

- **Task done:** the checkbox morphs into a check, the row slides out of the open list; a light
  haptic on the phone.
- **Habit done:** its ring fills with a spring and a small burst.
- **Goal hit or streak milestone:** confetti (Konfetti on Android, a lighter burst on Windows).
  Nothing else gets confetti.
- **Opening the app:** the logo in the theme's colors draws its arrow in the middle of the screen,
  then the app fades in (about a second, once per start; a tap skips it).
- **Opening a task:** its row grows into the details, and leaving drains them back into the row
  while the list comes back up (Android; a back swipe drives it with the finger). Other screens
  slide in a little and fade.

Durations: quick 120 ms (state changes), standard 250 ms (moves, sheets), emphasized 400 ms
(celebrations). **Reduce motion** follows the system setting and has an in-app switch; it turns
movement into short fades and skips bursts and confetti.

**Sound:** silent by default. An optional completion tick can be switched on in Settings.

## Layout

### The composer

A floating pill at the bottom (phone) or of the main pane (Windows), on Today, Tomorrow, the Inbox,
Wants, Habits and Goals (ADR 0016). Its round button at the end is a **plus** in the primary color
while the line is empty, which opens the page's full form (a bottom sheet or the existing dialog on
the phone, the page's panel or editor on the PC), and the **send arrow** once something is typed: the
plus spins out and the arrow lifts in over the quick and standard durations, a cross-fade under
reduce motion. In the quick chat it is always the arrow. As you type, it **grows a
preview** above the text: chips for the parsed date and time, area, tags, project, top priority and
idea type, exactly as the item will be saved (the grammar is in the spec's "Composer and
shortcuts"); on Wants, Habits and Goals the price, wait and reason, how often and how much, or the
period and target, with a danger chip for a want's missing reason. Enter or Send saves; Esc clears;
Ctrl+N opens the full form on the PC; tapping a task chip edits or removes it. A line starting
with `/` becomes a command instead.

On Windows the switch to the quick chat sits at the pill's start: a round button with a sparkle,
tonal when off and in the primary color when on. The chat's thread sits on a raised card above the
pill, with its corners, the theme's surface, a soft shadow and a hairline edge.

### Today

Top to bottom: the date and a one-line summary ("3 of 7 done, 2 habits left"), top priorities,
timed items (tasks with a time and reminders), other tasks, then overdue items and this week's goals,
both collapsed. Today's habits are cards (the habits redesign, [habits](../habits.md#on-screen)): on
the phone behind a Tasks / Habits switch under the date, on Windows in a panel beside the tasks (under
them where the window is narrow). A limit is never counted as left, and once every habit is done a
short card on the hero colors says so.

### Goals

The owner's pick from the habits, goals and add prototypes (2026-10-01): the horizon rings on top,
then the ladder from the year down to today, each goal as a card ([goals](../goals.md#on-screen)).

- **Rings:** four rings (year, month, week, today) in the accent with the percentage inside in the
  number style, the horizon's name and "1 of 3 hit" under it. The ring that filters the page sits on
  a surface tile with an accent edge.
- **Rungs:** a small accent badge with the horizon's letter, the period's dates in the strong body
  style and a muted line ("1 of 3 hit · Day 5 of 7"). The phone draws a rail down the left with a
  tick to each card; the PC sets the four rungs side by side as columns.
- **Card:** a surface card with the theme's card corners: a small ring (or the box for a done-or-not
  goal), the title in the strong body style, a muted "Feeds ..." line, the big number in the number
  style and the accent with "of 25 km" beside it, a bar in the accent over a faint track, and a pace
  pill: neutral when on track, a faint danger when behind, the full accent with onAccent text on a
  hit. The quick log is a tonal pill ("+5 km"). The PC's cards are compact (a 20 px number, a 6 px
  bar).
- **Chain:** while a chain is lit, the other cards fade to about a third and the chain's cards get a
  2 px accent edge (3 px on the picked one). A ring's filter hides the other rungs on the phone and
  fades the other columns to about a third on the PC.

### Navigation (ADR 0014, from M8)

**Phone.** The bottom bar has five tabs: four places the owner pins (Today, Tomorrow, Inbox and
Projects by default) and **Places**. Places is a page of live tiles, two columns, one per place:
Today and Habits and Goals as rings, Inbox and Wants as big counts, Tally as its stacked bar, the
Letter on a hero tile, the rest with their one-line summary. A pinned tile carries a small pin. The
line under the title says what is in the bar and has **Edit**: the tiles wiggle (unless reduce
motion is on) and each shows a pin toggle, filled in the accent when pinned. At four pins the unpinned
tiles grey out and the line says to unpin one first; the last pin can't be removed. A place opened
from Places shows a back arrow to it. The Places tab shows a count for what waits in places that
are not pinned. The top bar keeps sync, Plan tomorrow and Settings.

**Windows.** The sidebar starts with **Go to…** (Ctrl+K), then a **Pinned** group (Today,
Tomorrow, Inbox and Projects by default, no limit), then **All places**, then Settings at the
bottom, with Activity and Areas and tags beside it. A click on All places (or Enter) opens the
**Places page**, the phone's hub on the PC: the same live tiles, two to four to a row as the window
allows, the pinned ones marked, and **Edit**, where a click on a tile pins or unpins it (no limit,
the last pin stays). Only the arrow at the side of All places, or Right and Left, folds and unfolds
the places that are not pinned under it. A place pins or unpins itself from the Pin toggle in the
title bar, or from a right click on its sidebar item. Ctrl+K opens a box that filters places as you
type (All places is one of them); arrows move, Enter opens, Esc closes. The sidebar still collapses
to icons and remembers that.

Pins are a device setting, so the phone and the PC keep their own. New places (Wants, Tally) arrive
unpinned, on the Places page and under All places.

### Windows

The area and tag filters live on each list's toolbar line, with the sync state and a filled Plan
tomorrow button, all on one line ([lists](../lists.md#filtering)).

## Identity

The mark is a **G whose middle turns into a trend line** that zigzags up and leaves through the G's
opening as an arrow. It is drawn once in `tools/generate_app_icon.py`, which writes the Windows
`.ico`, the Android adaptive icon's vector layer, [`brand/goalmaker-icon.svg`](brand/goalmaker-icon.svg)
and `contracts/design/logo.json`. The app icon uses Track's colors in every theme: a black tile, a
white G, a volt arrow. The "GoalMaker" wordmark appears on the sign-in screen only.

Inside the apps the mark takes each theme's logo colors (`themes.json`, `logo`), one brand color on
the tile and the other on the arrow: Electric violet and coral, Night indigo and cyan, Sunrise
magenta and tangerine. When the theme changes it recolors and draws its arrow again (the owner's
idea, 2026-09-19). It sits on the sign-in screen and in Appearance; on Windows the window, taskbar
and tray icons follow the theme too. The Android launcher icon stays Track's.

**Emoji:** areas, goals and habits may carry one optional emoji, shown next to the name and on
widgets.

## Settings

Appearance holds: theme (four cards with a small preview), mode (system, light, dark), pure black,
reduce motion (default: follow the system), completion sound.

Settings is one page that scrolls, with no Save button: every change saves at once. It is grouped
into sections, each one card with a title, a one-line description, then rows split by thin
dividers. The order: Problems (only while there are some), Account, Appearance, Planning day and
reminders, Areas and tags, Claude, Your data (ending with its danger zone), Updates, About, and
Developer in a dev build. On the PC Quick add, Startup and Mini windows sit before Your data. Areas
and tags shows the areas in use with their colors and the tags, and opens the full manager.

**Jump navigation.** Only with 4 or more visible sections; with 3 or fewer the page is short enough.
On the phone a row of chips under the app bar that stays put (chips 32 dp, 8 dp apart, the current
one filled with primary and scrolled into view); on the PC a list on the left, which becomes the
chip row on a narrow window. The current section is the last one whose top passed a line 80 dp
(px on the PC) below the top of the scroll area, or the last section at the very bottom; a jump's
target stays current while the page scrolls there and after it lands, until the owner scrolls.

**Jump hint.** A chip, a list item or a deep link (the update notification and the mark on the gear
land on Updates) scrolls the page in 250 to 450 ms by distance, decelerating. If the owner scrolls
during it, the jump stops and no hint plays. Once it lands the card lights up: a tint of the
theme's brightest accent, a 2 dp inner ring, a 4 dp soft glow, a 3 dp bar growing on its left
edge and the title in the highlight color; 150 ms rise, 350 ms hold, 700 ms fade. Focus, and
TalkBack, move to the section title.

**Scroll hint.** Scrolling into a new section by hand grows only the edge bar (to 85%) and colors
the title, 120 ms rise and 580 ms fade, once the page has been still for 150 ms. A fast fling never
flashes the sections it passed.

**Reduce motion** (the system's animator scale at 0, or the app's switch): the jump scrolls at
once, then a static tint and ring show for 900 ms with no glow and no fade; scrolling plays no hint.

The highlight colors are theme tokens (`highlight` in `contracts/design/themes.json`): the spot is
the theme's brightest accent, mixed into the card at 9 to 12% (30% for Track's volt on light); the
ring is the spot at 60 to 70% (black for Track on light, where volt is too pale to see); the glow is
the spot at about 20% (55% for volt on light); text and the title keep 4.5:1 on the tinted card.
The rules that can be checked live in `contracts/vectors/settings.json`, which both apps' tests read.

**Rows.** Each row is at least 56 dp high on the phone (48 px on the PC), with touch targets of
48 dp, a title and one line of hint in the muted color.

| Row | Use |
| --- | --- |
| Toggle | On or off; the whole row is the switch, named by its title |
| Segmented | 2 to 4 short options (System, Light, Dark) |
| Choice cards | A visual pick, like the theme cards |
| Dropdown | 5 options or more, like the hour the day starts or the review day |
| Text field | A free value, like the quiet hours' times; saves on Enter or when focus leaves |
| Slider | A range where the feel matters, like a reminder's time; shows its value, saves on release |
| Button row | An action with a result, like Export or Check now; the result replaces the hint |
| Link row | Opens a page in the app (›) or outside it (↗); the whole row is the target |
| Info row | A read-only value, selectable |
| Danger row | An action that can't be taken back, inside the danger zone of its section |

A text field checks its value on Enter and when focus leaves, not on every key: a bad value turns
the border red, says why under it with an icon, and is never saved, so the last good value stays in
effect. Each saved change shows "✓ Saved" next to its control (120 ms in, 1.5 s, 250 ms out), and a
screen reader hears "Saved" once. A disabled row fades to 45% and its hint says why (pure black:
"Only in dark mode"). An empty list is one muted line that says how to fill it.

**Danger zone.** Actions that can't be taken back sit at the end of the section they affect, in a
block outlined in the danger color and labelled "Danger zone": Restore from a file at the end of
Your data, and Sign out anyway in Account while changes haven't reached the server. Each asks first
in a dialog with Cancel focused.
