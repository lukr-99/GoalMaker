# M5-04: Android widgets

**Status:** done · **Milestone:** M5

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

## Result
- Glance 1.2.0 (new in the version catalog) with `TodayWidget` and `HabitsWidget` in `ui/widget/`,
  their receivers in the manifest and their sizes in `res/xml/`.
- `WidgetContent` works out what each widget shows from `ListRules` and `HabitRules`, so a widget can
  never disagree with the app; `WidgetContentTest` covers it over a real replica (5 cases).
- A tap finishes a task or checks a habit in through the same lists the screens use, then draws both
  widgets again; a background sync refreshes them too. `WidgetSkin` follows the owner's theme.
- Today lists what is still open, as the app's Today does, so a tick takes the row away and the
  header counts it. A habit measured by an amount opens the app, since it asks for a value.
- Not yet checked on a home screen: both devices were away when this landed. docs/widgets.md.
