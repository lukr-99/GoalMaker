# M4-03: Habits: data, cadences and streaks

**Status:** todo · **Milestone:** M4

## Scope
- Migration and replica migration: `habits` (name, emoji, cadence daily, weekdays, per week or per
  month with days or a count; measure check, count or amount with target and unit; linked goal;
  paused ranges; archived) and `habit_checkins` (day, value, skipped), synced (spec, stories 36 to
  41; data model).
- The habit rules, pinned by `contracts/vectors/habits.json` for Kotlin, C# and the connector:
  which days a habit is due, whether a period is met (a day's value reaches the target; a week's
  check-ins reach N), streaks counted in periods met (days, or weeks for "3 times a week"), skipped
  periods and paused ranges that neither break nor count, and the heatmap's values.
- Check-ins of a habit linked to a numeric goal with the same unit count toward the goal (story 32),
  added to the goals vectors.

## Acceptance criteria
- pgTAP for both tables; every vector case passes in all three implementations.
