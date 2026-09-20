# M4-08: Goals and habits through the connector

**Status:** done · **Milestone:** M4

## Scope
- Tools to read goals (per period, with progress) and habits (today's, streaks), add and edit goals,
  log an amount, and check in a habit (spec, stories 70 and 71), over the same rules the apps run.
- The review prompts gain the period's habits, goal progress, highlights and data-reactive prompts.

## Acceptance criteria
- Endpoint tests on the local stack; a check-in made by Claude shows in both apps with "by Claude".

## Notes
- The planner learned goals and habits (read, add, edit, status, log an amount, check in, skip), and
  the tools `get_goals`, `add_goal`, `update_goal`, `set_goal_status`, `log_goal_amount`, `get_habits`,
  `check_in_habit` and `skip_habit` run on them. A check-in keeps the name-based id the apps give it,
  so Claude's check-in and the phone's are one row.
- The weekly and monthly review prompts now carry the period's goals with their progress, how each
  habit held up, and what the period's data asks about. `reactive()` was split so its order and
  thresholds live in `triggers()`, which the prompts read without shipping the prompt library.
- The endpoint test on the local stack covers both: a goal set, counted, changed and marked done, and
  a check-in and a skip, each landing as the owner's row with the activity log showing Claude.
- Fixed on the way: `deno task check` was failing on main (`moved_count` took `number | undefined`).
