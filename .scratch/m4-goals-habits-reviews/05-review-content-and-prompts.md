# M4-05: Review prompts: the library and data-reactive prompts

**Status:** todo · **Milestone:** M4

## Scope
- `contracts/content/prompts.json`: a versioned library of about 100 reflection prompts with
  categories (wins, lessons, energy, focus, gratitude, ...), which reviews they suit (weekly,
  monthly, yearly) and optional triggers (spec, stories 62 and 63; Reviews content).
- The rotation (no prompt again until the others in its category were shown) and the data-reactive
  rules (a habit missed most of the week, a task moved three times or more, a goal ahead of or
  behind plan) over the period's statistics, pinned by `contracts/vectors/reviews.json` (new
  sections) for Kotlin, C# and the connector.
- `reviews.reflections`: the prompt ids and answers, which needs a JSON column kind in the
  synced-tables contract and both sync engines.

## Acceptance criteria
- The content file validates in CI; every vector case passes in all three implementations.
