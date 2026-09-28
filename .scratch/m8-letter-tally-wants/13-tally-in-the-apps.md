# M8-13: The Tally place, and Tally in stats and the review

**Status:** todo · **Milestone:** M8

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
