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

## Quick add

A pill on the home screen (spec, story 87). Tapping it opens the composer over whatever was on
screen, with the keyboard already up and the app itself staying shut; **Today** on the right opens
the same box with the line already planned for today. What is typed is a composer line like any
other, so `#tag`, `@Area`, `+Project`, a date and a time all work, and a line with no day waits in
the Inbox to be sorted later ([composer](composer.md)). Saving closes the box and returns the owner
where they were; a tap outside closes it without saving.

A home screen widget cannot hold a text field of its own, because the launcher draws it and has no
keyboard, so the typing happens in `QuickAddActivity`, a see-through window the widget starts.

## How they are built

`ui/widget/` holds them: `WidgetContent` works out what each widget shows from the same rules the
screens use, which is what the tests cover; `TodayWidget`, `HabitsWidget` and `QuickAddWidget` draw
it with Glance, the last one over `ui/capture/`, the same composer the share sheet uses;
`CompleteTaskAction` and `CheckInHabitAction` write the tap and draw the widgets again. `WidgetSkin`
takes the owner's theme and mode, so a widget matches the app rather than the launcher.

A widget is drawn again after a tap on one, after a background sync brings changes in, and every half
hour by the launcher.
