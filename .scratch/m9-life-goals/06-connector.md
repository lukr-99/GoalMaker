# M9-06: Life goals through the connector

**Status:** planned · **Milestone:** M9

## Scope
- `get_life_goals` (open first, with time left and how many pictures), `add_life_goal` and
  `update_life_goal` (title, why, by, area, status), made by Claude, with undo. In
  `supabase/functions/_shared/tools`, so the quick chat gets them too, minus deletes.
- `docs/connector.md` and a sample prompt.

## Acceptance criteria
- Endpoint tests and snapshots for the three tools.

## Check
- Through the endpoint against the local stack: add a life goal, read it, achieve it, undo.
