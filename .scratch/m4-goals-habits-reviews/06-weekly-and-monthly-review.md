# M4-06: The weekly and monthly review in both apps

**Status:** in progress · **Milestone:** M4

## Scope
- The guided review (spec, stories 59 to 61 and 64): look back (done tasks, the habit heatmap, goal
  progress, highlights such as the longest streak, the best day and the strongest area, and a
  comparison with the last period), handle open tasks, reflect with prompts from the library and
  the data-reactive ones, rate mood and energy 1 to 5, set next period's goals, glance at next week.
- The saved review (a `reviews` row) with past reviews to read back, including summaries Claude
  saved (story 65); mood and energy charted.
- The weekly and monthly ritual reminders at times the owner chooses, like the evening reminder
  (story 55; `ritual_runs` already has these rituals).
- A nudge in January to set yearly goals (story 66).

## Acceptance criteria
- View-model tests on both apps; a review started on one app can be read on the other.

## Notes (first half, committed)
- The guided review runs on both apps: look back (tasks done day by day against the period before, the
  best day, the strongest area, the longest streak, the period's goals and habits), handle what is still
  open, answer three prompts (the period's own first), rate mood and energy, and set the next period's
  goals. `ReviewList`, `ReviewLookBack` and `ReviewDigest` are shared shapes in both apps; the answers
  land in `reviews.reflections`.
- Past reviews are listed on a Reviews screen (Android: the overflow menu on Today; Windows: the sidebar
  and `--open reviews`), and any of them opens again to be read or carried on.
- Tasks now count their moves (`tasks.moved_count`, Supabase 0012, replica 0007, PlanRules.moves pinned
  by the 'moves' section of vectors/plan.json), which is what the slipping-task prompt reads.

## Still to do here
- The weekly and monthly ritual reminders at times the owner chooses (story 55), like the evening one.
- A nudge in January to set yearly goals (story 66).
- Mood and energy charted over time (the review list shows the numbers today); M4-07 stats will carry it.
