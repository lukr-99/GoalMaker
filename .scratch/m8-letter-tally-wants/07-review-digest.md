# M8-07: The review digest through the connector

**Status:** in progress (the check on the cloud project is left) · **Milestone:** M8

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

## Result

2026-09-28, the connector.

- `rules/digest.ts` builds one period: done (in the order done), left, earlier, overdue and slipping
  tasks, the goals against where they should be by now, each habit's met, missed and skipped periods
  and streak, project items done, the facts and triggers, this period's review and the last letter,
  the next period's tasks, deadlines and goals, and the wants that became ready, were decided, or are
  ready next. `defaultPeriod` is the week or month holding yesterday's planning day.
- `reviewDigest` in `_shared/planner` reads the owner's data into it. `get_review_digest` returns it
  as JSON with names for areas and projects, and the `weekly_review` and `monthly_review` prompts are
  now built from it instead of `lookBack()`.
- `save_review_summary`'s description names the Letter and says to pass the digest's period start.
- Tally has no section yet: it arrives with M8-10 to M8-14 (`docs/letter.md` says so).
- **Checked:** the prompt snapshots over a fixed August were taken before the change and still match
  after it. The `digest` group of `reviews.json` (7 default periods and the fixed week, section by
  section) passes in `deno task test`, and the Kotlin and C# review tests still read the file. The
  endpoint test on the local stack: a seeded week whole in one call, the default period for weekly
  and monthly (against the rule, since the function's clock can't be set; the Sunday and Monday
  cases are in the vectors), a bad period refused, and a monthly digest. The apps are untouched.
- **Left:** the tool on the cloud project from a Claude chat, once the connector is deployed.
