# The home screen widgets

Two Glance widgets put the day on the phone's home screen (spec, stories 85 and 86). Both read the
device's replica, so they show what the app shows, and both write through the outbox, so a tap syncs
like any other change and turns up on the PC.

## Today

The tasks still open today, in the order Today lists them: top priorities first, then the ones with a
time, then the rest. The header counts what is behind ("Today · 1 of 3 done"). A tap on a row finishes
that task, so the row leaves the widget and the header moves; a tap anywhere else opens the app.

## Habits

The habits due today, each with a mark for whether its period is met, its emoji and, for a habit that
counts something, how far it has got. One tap checks a habit in, exactly as tapping its ring in the
app does. A habit measured by an amount asks for a value, so its row opens the app instead of
guessing one.

## How they are built

`ui/widget/` holds them: `WidgetContent` works out what each widget shows from the same rules the
screens use, which is what the tests cover; `TodayWidget` and `HabitsWidget` draw it with Glance;
`CompleteTaskAction` and `CheckInHabitAction` write the tap and draw the widgets again. `WidgetSkin`
takes the owner's theme and mode, so a widget matches the app rather than the launcher.

A widget is drawn again after a tap on one, after a background sync brings changes in, and every half
hour by the launcher.
