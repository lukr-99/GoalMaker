# M4-01: Goals: data and progress rules

**Status:** done 2026-09-19 (habit check-ins join the number mode with M4-03) · **Milestone:** M4

## Scope
- Migration and replica migration: `goals` (horizon year, month, week or day; period start; optional
  parent of a longer horizon; progress mode done, tasks or number; target and unit; status; emoji;
  position) and `goal_entries` (amounts logged on numeric goals), synced like every table, and
  `tasks.goal_id` so tasks can serve a goal (spec, stories 27 to 31; data model).
- The progress rule, pinned by `contracts/vectors/goals.json` and run by Kotlin, C# and the
  connector: done/not done; the share of linked tasks done; or the sum of entries (plus check-ins of
  a linked habit with the same unit, once habits exist) against the target. A period's start for
  each horizon, and what "hit" means.
- The cascade: a parent must have a longer horizon and a period containing the child's.

## Acceptance criteria
- pgTAP row security and checks for both tables; the migration harness passes.
- Every vector case passes in all three implementations.

## Result
- Supabase migration 0009 (`goals`, `goal_entries`, `tasks.goal_id`; 14 pgTAP checks; fixtures),
  replica migration 0004, and the synced-tables contract (goals before tasks, since tasks point at
  them). `undo_activity` now finds undoable tables by their log trigger, so new tables need no change.
- `contracts/vectors/goals.json` (periods, parents, progress, copies) with `GoalRules` in Kotlin and
  C# and `rules/goals.ts` in the connector; all pass. docs/goals.md.
