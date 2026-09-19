# M4-02: Goals in both apps

**Status:** done (2026-09-19) · **Milestone:** M4

## Scope
- A Goals screen (Android: a tab or a Today section link; Windows: Goals in the sidebar): goals per
  period (this year, month, week, today) with a progress ring or bar, and the cascade as a tree
  (spec, stories 33 and 34).
- Create and edit a goal: name, emoji, horizon and period, optional parent, progress mode, target
  and unit. Log an amount on a numeric goal ("+5 km"). Link a task to a goal from its detail view.
- "Copy last week's goals" when a new week has none (story 35). Hitting a goal gets confetti
  (design spec, level 3), skipped under reduce motion.
- This week's goals on Today, collapsed (design spec, Today).

## Acceptance criteria
- View-model tests on both apps over a real replica; goals made on one app show on the other.

## Done
- Android: Goals screen (flag in Today's top bar), rings, tree, editor, logging, copy, Konfetti,
  this week's goals folded on Today, the goal picker in task details.
- Windows: Goals page in the sidebar with the same parts and a lighter confetti burst, this week's
  goals folded on Today, the goal picker in task details.
- Both: GoalList on the replica, tasks.goal_id, the activity log names goal changes and undoes them.
- Tests: GoalListTest/GoalListTests, GoalsViewModelTest(s), the task goal pickers, activity vectors.
