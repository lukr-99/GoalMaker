# M2-04: Composer grammar and its contract vectors

**Status:** todo · **Milestone:** M2

## Scope
- `contracts/vectors/composer.json`: lines and the draft each should give (title, planned date and
  time relative to a fixed "now" and the 04:00 day rollover, `#tag`, `@Area`, `!`, `?`,
  `+Project`, `every ...` presets, `/commands`), including edge cases (escaping, a `#` inside a
  word, unknown areas, past times). English date words in v1; Czech arrives with the Czech UI.
- A pure parser in Kotlin and in C#, both passing every vector.

## Acceptance criteria
- Both suites pass the same file; the preview never disagrees with what gets saved.
