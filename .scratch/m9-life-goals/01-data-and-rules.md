# M9-01: Life goals: data and rules

**Status:** built, waiting for review · **Milestone:** M9

## Scope
- Supabase migration 0023: `life_goals` (title, why, by date, area, status `open | achieved |
  dropped` with the time it changed, position, made by) and `life_goal_pictures` (life goal, position,
  width, height) with the usual synced columns, row security, the activity log trigger, undo, the
  purge, grants and the Realtime publication. The private bucket `life-goal-pictures` (2 MB, JPEG
  only) with Storage policies that keep each owner in their own folder.
- Replica migration 0017 and both tables in `contracts/schemas/synced-tables.json`; the backup
  carries them in the synced-tables order.
- `LifeGoalRules` in Kotlin, C# and `rules/life-goals.ts` (time left and order; the why reminder's
  moment and life goal in Kotlin and C# only).
- `LifeGoalList` in both apps over the replica: add, edit, achieve, drop, reopen, delete (taking its
  pictures), reorder, and the picture rows.

## Acceptance criteria
- pgTAP: owner can, stranger can't, anonymous can't, on both tables and on the bucket's objects; the
  checks (why required, status and its time together). The migration harness passes in full and as
  0022 to 0023 with fixtures; `tools/check_synced_tables.py` passes.
- Every `life-goals.json` case passes in Kotlin, C# and (for its groups) TypeScript.
- A backup round trip keeps life goals and picture rows.

## Vectors to add
- `contracts/vectors/life-goals.json` (new, listed in `contracts/README.md`): `timeLeft` (years,
  the day before a whole year, months, a 31st into a shorter month, days, today, past, none),
  `order`, `fnv1a`, `whyMoment` (each frequency, quiet hours), `whyGoal` (rotation, one goal, none
  open), `whyDue` (what a look shows, a missed moment caught up, only the latest), `whyNext` (the next
  moment for the alarm).
- `contracts/vectors/backup.json`: the table order with the two tables.

## Check
- Emulator and Windows: nothing visible yet; both replicas migrate from 0016 with data.

## Result

2026-10-04.

- Supabase migration 0023 (`life_goals`, `life_goal_pictures`, the made-by trigger reused from tasks,
  the purge, and the private `life-goal-pictures` bucket with four Storage policies on the owner's
  folder), its fixtures and 19 pgTAP checks. Replica migration 0017 with a fixture; both tables in
  `synced-tables.json` and the backup order.
- `contracts/vectors/life-goals.json`, made by a reference script and checked by hand, with
  `LifeGoalRules` and `WhyReminder` in Kotlin and C# (every group) and `rules/lifeGoals.ts` (time
  left and order). `LifeGoalList` on both apps (add, edit, achieve, drop, reopen, reorder, delete with
  the pictures' rows and restore them together, picture rows), tested on a real replica.
- **Changed from the plan:** time left counts months the simple way (January 31 to February 28 is not
  a month), and the why reminder got a `whyDue` group: a look shows only the latest moment since the
  last look, the same "since, now" pattern the wants notification uses.
- **Not run locally:** the Supabase migration harness and pgTAP. Windows had reserved the stack's
  ports (55319 to 55418) for Hyper-V, so `supabase start` could not bind 55322. CI runs both.
