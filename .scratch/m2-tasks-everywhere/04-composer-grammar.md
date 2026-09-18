# M2-04: Composer grammar and its contract vectors

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- `contracts/vectors/composer.json`: lines and the draft each should give (title, planned date and
  time relative to a fixed "now" and the 04:00 day rollover, `#tag`, `@Area`, `!`, `?`,
  `+Project`, `every ...` presets, `/commands`), including edge cases (escaping, a `#` inside a
  word, unknown areas, past times). English date words in v1; Czech arrives with the Czech UI.
- A pure parser in Kotlin and in C#, both passing every vector.

## Acceptance criteria
- Both suites pass the same file; the preview never disagrees with what gets saved.

## Result
- The rules are written down in `docs/composer.md` (where dates count, "the last one wins", the
  planning day and the rollover, repeat first occurrences, RRULE form).
- `contracts/vectors/composer.json`: 84 cases written as marked-up lines, so every span's offsets
  come from the markup instead of hand counting. Kotlin (`domain/composer`) and C#
  (`GoalMaker.Core.Composer`) pass all of them; a deliberately broken copy fails 4 of 84.
