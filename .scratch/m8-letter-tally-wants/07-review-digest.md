# M8-07: The review digest through the connector

**Status:** todo · **Milestone:** M8

## Scope
- `get_review_digest(kind = weekly | monthly, period?)`, read-only (spec, story 103; ADR 0012;
  [letter](../../docs/letter.md)): done, open, overdue and slipping tasks, goals with progress,
  habits met, missed and skipped with their streaks, project items moved to Done, the triggers, this
  period's mood, energy and reflections, the last period's letter, the next period's plan, and the
  wants that became ready or were decided.
- One builder in `_shared/planner`, used by the tool and by the `weekly_review` and
  `monthly_review` prompts, which today build their own text from `lookBack()`.
- The default period is the one holding **yesterday's** planning day, so a Sunday evening run and a
  Monday morning run both mean the week just finishing.
- `save_review_summary`'s description names the Letter and says to pass the digest's period start.

## Acceptance criteria
- The prompts' text is unchanged for the same data (a snapshot test before and after the builder).
- Endpoint test: a seeded week comes back whole in one call; the default period on a Sunday and on a
  Monday; a monthly digest.

## Vectors to add
- `contracts/vectors/reviews.json`: a `digest` group, run by the TypeScript rules: the default
  period (Sunday evening, Monday morning, 02:00 before the rollover, the 1st of a month) and the
  digest of a small fixed week, section by section.

## Check
- Emulator and Windows: nothing changes.
- Endpoint: the tool through MCP on the local stack, and on the cloud project from a Claude chat.
