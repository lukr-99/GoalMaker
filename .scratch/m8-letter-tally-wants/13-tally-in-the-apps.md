# M8-13: The Tally place, and Tally in stats and the review

**Status:** in progress (the Windows window check is left) · **Milestone:** M8

## Scope
- Android, then Windows (spec, stories 109, 110, 112 and 113):
  - **The Tally place:** today as one stacked bar by category, the week as stacked bars per day,
    time per project, and filter chips (Phone, PC, a category) in the main area; the switch or the
    usage access card at the top; categories and rules to add and edit.
  - **Stats:** a Tally block over twelve weeks.
  - **The review's look-back:** a Tally block for the period.
- Category colors from the area palette, bars drawn like the existing stats charts and growing in
  with the standard duration, tabular numbers.

## Acceptance criteria
- View-model tests: the bars from totals, the filters, a rule added on one app sorting the next day
  on the other.
- The numbers match the connector's for the same week.

## Vectors to add
- `contracts/vectors/tally.json`: a `weeks` group (twelve weeks, per category, per device kind) if
  the stats block needs its own shape.

## Check
- Emulator: the place with phone and PC data, the filters, a new rule, stats and a review.
- Windows: the same, in a narrow and a wide window.
- Endpoint: nothing.

## Result

2026-09-30, both apps.

- **Shared:** a `weeks` group in `contracts/vectors/tally.json` (the twelve weeks ending with today's,
  filtered by device kind and category), run by `rules/tally.ts`, Kotlin and C#; `navigation.json`
  lists Tally as a place after Wants.
- **The Tally place** on both apps, pinnable and in the Places hub (Windows: the sidebar, Ctrl+K and
  `--open tally`): the switch (and on the phone the usage access card) moved here from Settings;
  filter chips for Phone, PC and each category with time this week; today as one stacked bar; the week
  by day; time per project; and the owner's rules and categories to add, edit and delete (both
  `TallyList`s gained update and delete; a deleted category's time shows as "A removed category").
  Category colors are the palette's swatches, numbers tabular, and bars grow in over the standard
  duration or fade in with reduced motion.
- **Stats:** "Where the time went", twelve weeks of stacked columns, once there is Tally time.
- **The review's look-back:** the period's time by category, once there is Tally time.
- **Checked:** 404 Android tests, build, lint; 521 Windows tests, `dotnet format`; the accessibility
  scan; 55 Deno cases. On the emulator, the place with the phone's real minutes (Other, Video, Chat)
  and the Places tile. On Windows, the offscreen page snapshots wide, narrow, filtered to the phone
  with the add panel open, stats and the review (`PageSnapshots`, explicit).
- **Left:** the Windows app in a real window with the PC's own minutes, and a rule added on one app
  sorting the next day on the other, once both devices run Tally.
