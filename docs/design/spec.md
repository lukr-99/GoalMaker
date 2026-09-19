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
  follow the system text size and are tested at the largest setting.

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
`accent`/`onAccent` (checks, progress, rings), `hero`/`onHero`/`heroAccent` (big-number cards),
`danger`. Material 3 on Android and WPF UI on Windows get their own color slots filled from these
roles, so built-in controls match.

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

Durations: quick 120 ms (state changes), standard 250 ms (moves, sheets), emphasized 400 ms
(celebrations). **Reduce motion** follows the system setting and has an in-app switch; it turns
movement into short fades and skips bursts and confetti.

**Sound:** silent by default. An optional completion tick can be switched on in Settings.

## Layout

### The composer

A floating pill at the bottom (phone) or of the main pane (Windows). As you type, it **grows a
preview** above the text: chips for the parsed date and time, area, tags, project, top priority and
idea type, exactly as the item will be saved (the grammar is in the spec's "Composer and
shortcuts"). Enter or Send saves; Esc clears; tapping a chip edits or removes it. A line starting
with `/` becomes a command instead.

### Today

Top to bottom: the date and a one-line summary ("3 of 7 done, 2 habits left"), top priorities,
timed items (tasks with a time and reminders), habits for today as a compact row of rings, other
tasks, then overdue items and this week's goals, both collapsed.

### Windows

A sidebar with Today, Tomorrow, Inbox, Habits, Goals, Projects, Calendar, Reviews and Stats, then
areas and tags as filters, and Settings at the bottom. It collapses to icons and remembers that.

## Identity

The mark is a **G whose middle turns into a trend line** that zigzags up and leaves through the G's
opening as an arrow. It is drawn once in `tools/generate_app_icon.py`, which writes the Windows
`.ico`, the Android adaptive icon's vector layer and
[`brand/goalmaker-icon.svg`](brand/goalmaker-icon.svg). The app icon uses Track's colors in every
theme: a black tile, a white G, a volt arrow. The "GoalMaker" wordmark appears on the sign-in
screen only.

**Emoji:** areas, goals and habits may carry one optional emoji, shown next to the name and on
widgets.

## Settings

Appearance holds: theme (four cards with a small preview), mode (system, light, dark), pure black,
reduce motion (default: follow the system), completion sound.
