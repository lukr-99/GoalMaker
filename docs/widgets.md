# The home screen widgets

Five Glance widgets put the day and the goals on the phone's home screen (spec, stories 85 to 87).
They read the device's replica, so they show what the app shows, and the ones that change anything
write through the outbox, so a tap syncs like any other change and turns up on the PC.

## Today

The tasks still open today, in the order Today lists them: top priorities first, then the ones with a
time, then the rest. The header counts what is behind ("Today · 1 of 3 done"). A tap on a row finishes
that task, so the row leaves the widget and the header moves; a tap anywhere else opens the app.

## Habits

The habits on Today: due today, not paused, and not kept off Today ([habits](habits.md)). The header
counts the ones left the way Today does, so a limit or a skipped habit never holds it up.
Each has a mark for whether its period is met, its emoji and, for a habit that counts something, how
far it has got. One tap checks a habit in, exactly as tapping its ring in the
app does. A habit measured by an amount asks for a value, so its row opens the app instead of
guessing one.

## Goals

The Goals screen's four rings, this year, month, week and today, each with how much of its goals is
done and "N of M hit" under it. The rings come from the screen's own board (`GoalBoard`), so the
progress and pace rules are the same and a dropped goal stays off. Glance has no ring that shows an
amount, so `RingBitmap` draws each one as a picture, with the ring's description for TalkBack. The
rings take the size the widget is given. A tap opens the Goals place.

## Motivation

The owner's own words ("Free time is not wasted time"), or their goals for this week, this month or
this year as plain written lines, in large calm type. The type follows the theme's headings (Track
slants it) and shrinks to fit however the widget is sized (`MotivationFit`): a short phrase fills
it and a long list still shows.

Placing the widget opens its configure screen, which picks the words or the goals; a long press on
the widget opens it again. What is picked is kept for that one widget, on this phone only
(`MotivationStore`, keyed by the widget's id), and goes when the widget is removed. Several
Motivation widgets can each show something else. The goals are read from the replica each time the
widget is drawn, so a new goal turns up with the next sync. A tap opens Goals when the widget shows
goals, and the configure screen when it shows the owner's words, so they can change them.

The configure screen only edits what that widget shows, so it is not behind the app lock, like the
widgets themselves ([sign-in](sign-in.md)).

## Quick add

A bar on the home screen (spec, story 87). Tapping it opens the composer over whatever was on
screen, with the keyboard already up and the app itself staying shut; **Today** on the right opens
the same box with the line already planned for today. The sparkle on the left, the composer's own
chat mark, opens the same box in chat mode ([assistant](assistant.md)): the thread shows above the
bar, and the box stays open for the answers until a tap outside closes it. The thread goes with the
box. Each of the widget's buttons is a pick of the composer's mode, the same pick as the switch in
the app, so the app's composer opens in the mode last picked. What is typed is a composer line like any
other, so `#tag`, `@Area`, `+Project`, a date and a time all work, and a line with no day waits in
the Inbox to be sorted later ([composer](composer.md)). Saving closes the box and returns the owner
where they were; a tap outside closes it without saving.

A home screen widget cannot hold a text field of its own, because the launcher draws it and has no
keyboard, so the typing happens in `QuickAddActivity`, a see-through window the widget starts.

## How they are built

`ui/widget/` holds them: `WidgetContent` works out what each widget shows from the same rules the
screens use, which is what the tests cover; `TodayWidget`, `HabitsWidget`, `GoalsWidget`,
`MotivationWidget` and `QuickAddWidget` draw it with Glance, the last one over `ui/capture/`, the
same composer the share sheet uses; `CompleteTaskAction` and `CheckInHabitAction` write the tap and
draw the widgets again. `WidgetSkin` takes the owner's theme and mode, so a widget matches the app
rather than the launcher. `WidgetOpen` opens the app, or the Goals place through the same kind of
request the wants notification uses.

The widget picker shows a preview of the Goals, Motivation and quick-add widgets on Android 12 and
later (`res/layout/widget_*_preview.xml`). A preview is a fixed layout, drawn before any theme is
known, so it uses the default theme's colors, light or dark with the system.

A widget is drawn again after a tap on one, after a background sync brings changes in, and every half
hour by the launcher; a Motivation widget also as soon as its configure screen saves. `Widgets`
draws them again by the ids the launcher keeps for each receiver (`WidgetKind`), not with Glance's
`updateAll`, which goes by the drawing class: R8 once folded those classes together and each widget
was drawn over with another's rows ([pitfalls](pitfalls.md)). `proguard-rules.pro` keeps the widget
classes apart as well.

When a widget's rows cannot be read (the replica would not open, or a rule throws), it shows a plain
card with "Open GoalMaker to see this." instead of Android's "Can't load widget" box, and the error
goes to `files/crash.log` (`android/tools/pull-debug-files.ps1 -FilePattern '\.log$'`). An error
while Glance draws is written there too.

A debug build installs next to the release app as "GoalMaker Dev", with its own rows, and its
widgets are labelled "(Dev)" in the picker.
