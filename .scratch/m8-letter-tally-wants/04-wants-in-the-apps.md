# M8-04: The Wants place in both apps

**Status:** done · **Milestone:** M8

## Scope
- Android, then Windows: the Wants place in the new navigation (spec, stories 104, 105 and 107).
  - Filter chips in the main area: Cooling, Ready, Decided (Ready first when anything is ready).
  - A row per want: title, price, and an accent ring counting the cooldown down, full and pulsing
    once when ready; the reason one tap away; bought and dropped with an optional note, and undo.
  - The add sheet: title, reason (required), link, price and currency, area, and the cooldown it
    will get, changeable.
  - The thresholds at the top of the place, editable, saved to the profile.
- `/want` in the composer opens the add sheet with the title filled in.
- Stats: a Wants block (bought against dropped, money not spent), drawn like the other blocks.

## Acceptance criteria
- View-model tests on both apps: add, the ring's progress, the filters, decide and undo, thresholds
  changed on one app read on the other.
- A want added on the phone shows on the PC after a sync, and the other way round.

## Vectors to add
- `contracts/vectors/composer.json`: `/want` with and without a title.

## Check
- Emulator: add from the place and from `/want`, watch a want turn ready (dev clock), decide, undo,
  change a threshold; all four themes; the largest text size.
- Windows: the same, plus the chips and the sheet by keyboard.
- Endpoint: nothing.

## Result

2026-09-28, both apps.

- **Shared:** Wants joins the places in `navigation.json` (after Projects, unpinned by default),
  `/want` is a known command in `composer.json`, and `activity.json` words a want's changes (added,
  bought, dropped, reopened, renamed) and the thresholds (edited). Kotlin and C# pass all three.
- **Android:** the Wants place (`ui/wants`): the thresholds line with the cooldowns sheet, Ready,
  Cooling and Decided chips with counts (Ready leads while anything is ready, then the owner's choice
  sticks), a card per want with a ring that counts the days down and pops once when ready, an accent
  border on ready wants, and a card that opens to the reason, the link, the last price found, a note
  and Bought or Drop it (Reopen and Delete once decided), with undo. The add and edit sheet requires
  the reason and shows the cooldown the price gives, with one day more or less. `/want` from any list
  opens it with the title. Places has a Wants tile and counts ready wants when Wants is not pinned.
  Stats has a Wants block (bought, dropped, not spent), shown even before any task is finished.
- **Windows:** the Wants page with the same parts, the add panel inline at the top, the thresholds
  panel, filter pills in the accent, an undo bar, `--open wants`, a sidebar entry under All places
  (pinnable), `/want` from any composer, and the Wants block in Stats.
- **Tests:** `WantsViewModelTest` (Android) and `WantsViewModelTests` (Windows), plus the contract
  runs above. Checked on the emulator (`/want`, add, pick days, ready, decide, stats) and on the
  Windows dev build through UI Automation (add two wants, pick days, ready first). The owner flagged
  the Windows filter pills as hard to read; their text now takes the theme's text color.
