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

A widget is drawn again after a tap on one, a second after the replica changes (a change made in the
app, or what a sync brings in, in the front or in the background), when the theme changes, and every
half hour by the launcher; a Motivation widget also as soon as its configure screen saves. `AppGraph`
watches the tables the widgets read (`WidgetData.TABLES`). `Widgets` draws them again by the ids the
launcher keeps for each receiver (`WidgetKind`), not with Glance's `updateAll`, which goes by the
drawing class: R8 once folded those classes together and each widget was drawn over with another's
rows ([pitfalls](pitfalls.md)). `proguard-rules.pro` keeps the widget classes apart, and keeps the
constructor Glance makes a tap callback with.

Glance keeps a widget's session running for about 45 seconds after it draws, and an update in that
time only composes again: `provideGlance` does not run, so rows it read would stay as they were. So
each redraw also leaves a new mark in the widget's Glance state (`Widgets.DRAWN`), and
`Widgets.fresh` reads the rows again when the mark changes. Without it, a second tap within those
seconds changed the task but not the widget.

A Spacer in a widget needs a width or a height. With only padding, Glance stretches it over the rest
of the widget and the rows under it go out of sight; `WidgetLayoutTest` lays Today and Habits out the
way a launcher does to catch that.

When a widget's rows cannot be read (the replica would not open, or a rule throws), it shows a plain
card with "Open GoalMaker to see this." instead of Android's "Can't load widget" box, and the error
goes to `files/crash.log` (`android/tools/pull-debug-files.ps1 -FilePattern '\.log$'`). An error
while Glance draws is written there too.

A debug build installs next to the release app as "GoalMaker Dev", with its own rows, and its
widgets are labelled "(Dev)" in the picker.

## Checking them on an emulator

Some widget bugs only show after R8, which a debug build skips. The `minified` build type is the dev
app (same id, local stack, the machine's debug key) shrunk by R8 with the release rules. It is not
debuggable, because R8 leaves most optimizations out of a debuggable build, so `run-as` cannot read
its `files/crash.log`; widget errors are in logcat too (tags `GoalMakerWidget` and
`GlanceAppWidget`). It is never published. `android/tools/check-widgets.ps1` builds it, installs it,
places every widget and saves what it sees:

```powershell
npx supabase start
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd <your AVD> -no-snapshot-save -no-boot-anim
powershell -ExecutionPolicy Bypass -File android\tools\check-widgets.ps1 -Serial emulator-5554
```

Before the first run, sign the app in and give it rows: Settings > Developer > Sign in and sync,
then "Sign in as dev@goalmaker.test", and add tasks, habits (a check, a count, a limit and an
amount) and goals in the app or on the local stack. The app keeps its data between runs.

The script places each widget through `WidgetPinActivity`, a dev-build-only activity that asks the
launcher to place one (`adb shell am start -n com.goalmaker.app.debug/com.goalmaker.app.ui.widget.WidgetPinActivity --es kind TODAY`),
and confirms the launcher's sheet. It leaves in `.scratch/widgets/<time>/` (or `-Output`): a
screenshot per widget, the text and descriptions of the app's views on that screen (`<kind>.txt`,
which also lists other widgets on the same page), `logcat.txt`, and `dex-check.txt`, which reads the
APK with `dexdump` and fails the run when R8 merged widget classes or dropped a tap callback's
constructor. `-BuildType debug` checks the plain debug build and pulls its crash log, `-Apk` installs
a given APK, and `-Kinds TODAY,HABITS` places only some. Every run places new copies; uninstall the
app to clear them (and sign in again).

Then check by hand, with `phone.ps1 tap` or `adb shell input`: a tap on a row finishes the task or
checks the habit in, twice within a few seconds too; a change in the app and a sync from the PC show
up a second later; a long press resizes; a tap elsewhere opens the app, Goals or the quick-add box.
