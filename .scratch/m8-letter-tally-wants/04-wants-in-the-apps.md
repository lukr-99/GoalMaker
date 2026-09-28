# M8-04: The Wants place in both apps

**Status:** todo · **Milestone:** M8

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
