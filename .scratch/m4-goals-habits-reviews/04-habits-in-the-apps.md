# M4-04: Habits in both apps

**Status:** done · **Milestone:** M4

## Scope
- A Habits screen on both apps: today's habits with a one-tap check-in (a count or amount asks for
  the value), the streak, and a heatmap per habit over months (spec, stories 38 and 42).
- Create and edit a habit (cadence, measure, target, unit, emoji, linked goal); skip a period; pause
  and resume (stories 40 and 41).
- Today shows today's habits as a compact row of rings (design spec, Today); a ring fills with a
  spring and a small burst, and a streak milestone gets confetti, both skipped under reduce motion.
- Goal progress adds `HabitRules.goalAmounts` (check-ins of habits serving a numeric goal in its unit)
  to the logged amounts, on both apps' goal rings and the Lists week section.
- Habit reminders and checking in from a notification or a widget wait for M5 (widgets) and a
  reminder design for habits.

## Acceptance criteria
- View-model tests over a real replica on both apps; a check-in on one app shows on the other.

## Notes
- Android: `application/planning/HabitList` plus `ui/habits/` (board, view model, screen, dialogs, ring
  with the spring and burst, heatmap, Today's ring row). Windows: `Core/Planning/HabitList`,
  `ViewModels/Habit*`, `Views/HabitsPage`, `Controls/HabitHeatmap`, and the ring row in ListTemplate.
- Goal progress on both apps now adds `HabitRules.GoalAmounts` to the logged amounts (story 32).
- An amount habit tapped on Today opens the Habits page's log panel on Windows, and an amount dialog
  on Android.
- Habit reminders and the widget still wait for M5, as the scope says.
