# M5-04: Android widgets

**Status:** todo · **Milestone:** M5

## Scope
- A Today widget (Glance) listing today's tasks with a tick that completes one without opening the
  app, and a line for what is left (spec, story 85).
- A Habits widget with a one-tap check-in per habit and its ring, the same rules as the app
  (story 86).
- Both write through the replica and the outbox, so a widget tap syncs like any change, refresh on
  a sync, and open the app on the matching screen when tapped.

## Acceptance criteria
- Tests over a real replica for what each widget shows and what a tap writes.
- A tap on the widget shows on Windows after a sync.
