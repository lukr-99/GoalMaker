# M4-07: Stats

**Status:** done · **Milestone:** M4

## Scope
- A Stats screen on both apps: tasks completed per week, goals hit per month, habit success, and
  mood and energy over time (spec, stories 64 and 67), with big numbers on hero cards and simple
  charts that follow the theme.
- The numbers come from a shared rule pinned by `contracts/vectors/stats.json`, so both apps show
  the same values for the same data.

## Acceptance criteria
- Every vector case passes in both apps; the screens render in every theme and mode.

## Notes
- `StatsRules` and `StatsDigest` in both apps, pinned by `contracts/vectors/stats.json`: tasks a week
  (12), goals a month (6), each habit over the window (met, periods, streak, best run) and the mood
  and energy of the last 12 weekly reviews. The same file pins the review look back, which M4-06 owed.
- The screens: Android from the overflow menu on Today, Windows from the sidebar or `--open stats`,
  both with three hero numbers, the two bar charts, a row a habit and the two rating lines. The
  energy line falls back to the muted text colour in a theme whose primary is its accent (Track).
- Three helpers moved to where both the look back and the stats can use them: `TaskItem.completedDay`,
  `HabitRules.periodsBetween` and `GoalRules.progressOf` (the goal board had its own copy).
- Still parked from M4-06: the January nudge to set yearly goals (story 66).
