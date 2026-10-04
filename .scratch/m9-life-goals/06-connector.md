# M9-06: Life goals through the connector

**Status:** built, waiting for review · **Milestone:** M9

## Scope
- `get_life_goals` (open first, with time left and how many pictures), `add_life_goal` and
  `update_life_goal` (title, why, by, area, status), made by Claude, with undo. In
  `supabase/functions/_shared/tools`, so the quick chat gets them too, minus deletes.
- `docs/connector.md` and a sample prompt.

## Acceptance criteria
- Endpoint tests and snapshots for the three tools.

## Check
- Through the endpoint against the local stack: add a life goal, read it, achieve it, undo.

## Result

2026-10-04.

- `planner/lifeGoalList.ts` and `tools/lifeGoalTools.ts`: `get_life_goals` (open first, then closed;
  a `status` filter; time left, by date, area, "by Claude", the picture count and the why),
  `add_life_goal` (a why is required, with our own message; `by` or `in_years`, not both) and
  `update_life_goal` (one UPDATE, so one undo takes it all back). No delete; pictures stay in the apps.
- The quick chat gets `get_life_goals` and a smaller `add_life_goal` (title, why, by), since the
  chat's tool list has a 16,000-character cap; it is at 15,990 now, so the next chat tool needs room
  made first.
- **Checked:** lint, check, fmt; 133 unit tests (7 new); the connector endpoint tests against the
  local stack (a new step: add with `in_years`, made by Claude, refusals, the picture count without a
  deleted one, order, a stranger, achieve, undo, drop and reopen, clearing the by date and area) and
  the quick chat endpoint tests.
