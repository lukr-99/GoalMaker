# Pitfalls

Mistakes this project has already made, kept short so they are not made twice. Add one when a bug
took longer to find than to fix.

## R8 folds Glance widget classes together, and updateAll draws into the wrong widgets

2026-10-02. On the 1.9.0 release APK the owner saw the Today and Habits widgets not showing what they
should. After a sync or a tap, the Today and quick-add widgets were drawn with the Habits rows, and
the Goals widget with the Motivation widget's lines. Debug builds were fine, because R8 only runs on
release. R8's class merging had folded `TodayWidget`, `HabitsWidget` and
`QuickAddWidget` into one class (`u52`, with a hidden id field), and `GoalsWidget` with
`MotivationWidget` into another. Glance's `updateAll` and `getGlanceIds` find a widget's placed
copies by `javaClass.canonicalName`, so `TodayWidget().updateAll()` drew Today into every widget of
the group, then `HabitsWidget().updateAll()` drew over all of them. The launcher's own half-hourly
update was right, which made it look random. To see it, run `dexdump` on
`build/intermediates/dex/release/minifyReleaseWithR8/classes.dex` and compare the field types of the
`*WidgetReceiver` classes: the same type in two receivers means merged classes. The fix:
`-keep,allowshrinking class * extends androidx.glance.appwidget.GlanceAppWidget` in
`proguard-rules.pro`, and `Widgets` now draws by the launcher's ids per receiver
(`AppWidgetManager.getAppWidgetIds(ComponentName)`), never by `updateAll`. The same goes for any
library that looks a class up by its name at run time.

## WPF UI's dictionaries must load before GoalMaker's

2026-10-02, moving onto `DotNetLib.Tray`. The first try kept GoalMaker's dictionaries in App.xaml
and put the kit's (WPF UI's themes and controls) in front of them from `OnStartup`. Most pages
rendered, but the mini window snapshot failed with "Cannot find resource named
'Wpf.Ui.Controls.Button'": a style inside a template in `ListTemplate.xaml` is based on
`{StaticResource {x:Type ui:Button}}`, and WPF looks that up while the dictionary loads, not when the
template is used. Inserting WPF UI's dictionaries in front later does not help. `Theming/AppResources`
now merges the kit's dictionaries first and then GoalMaker's, in code, and App.xaml holds none.
Closed off by: `PageSnapshots` loads the same merge, and `MiniWindowsInEveryTheme` fails with the
message above when the order is wrong (checked). Explicit snapshots run one at a time, because they
share one `Application`.

## WPF UI fills its accent keys from Windows, or from shades of its own

2026-10-01. The owner saw a violet Plan tomorrow button and Send button next to a red title and red
checks. WPF UI colors primary buttons, toggles, checks and selections from dozens of accent keys.
On its first look at the application's resources it writes Windows' accent into them, and
`ApplicationAccentColorManager.Apply` turns any color into darker shades (light mode) or lighter,
greyer ones (dark mode), with black or white text by a brightness guess. Its theme dictionaries also
bake some of them into brushes once. None of those are GoalMaker colors. `ThemeApplier` now sets
every one of these keys itself on each Apply: the theme's primary, and onPrimary for text on it
(`ThemeApplierTests` seeds Windows' accent first and checks each key in every theme and mode). In
this case the violet was the theme's own primary, though: Electric pairs a violet primary
(buttons, send) with a coral accent (title, checks, section labels), by design. Before chasing a
color that "doesn't change", compare it with the theme's `primary` and `accent` in
`contracts/design/themes.json`, and with Windows' accent color.

## A Scaffold's bottomBar slot adds no insets

2026-10-01. The review's Next button sat under the phone's three-button navigation bar. The app
draws edge to edge, and Scaffold gives its `bottomBar` slot the whole bottom of the window. Material's
`NavigationBar` and `BottomAppBar` pad themselves for the navigation bar, so screens that use them
look right, but a plain `Row` or `Column` in that slot does not. `imePadding()` alone only helps while
the keyboard is up. Give a hand-made bar `navigationBarsPadding().imePadding()`;
`tools/check_accessibility.py` now fails on a `bottomBar` without it. Also, `padding(padding)` from a
Scaffold does not consume insets: follow it with `consumeWindowInsets(padding)` before `imePadding()`,
or the navigation bar is counted twice.

## An italic word swallows the space after it on Windows

2026-10-01. "The bank call moved *four* times" read "fourtimes" in notes and letters. The parser and
the runs were fine: the space was its own upright run. Each Windows font file is one face with its
own family name ("GoalMaker Archivo 400"), so `FontStyle = Italic` has no italic face to pick, and
WPF slants the upright glyphs instead. The slant moves the top of the last letter right, over the
space, while the advance width stays the same. Check with `Typeface.TryGetGlyphTypeface`:
`StyleSimulations.ItalicSimulation` means the italic is fake. Ship a real italic face when the family
has one (`tools/build_windows_fonts.py`), and leave room after a slanted word when it doesn't
(`GM.ItalicGap`).

## Ordering by a column the select turned into text

2026-10-01, PR 36. The connector's `get_activity` selected `id::text as id` and then said `order by
id desc`. Postgres sorts by the output column of that name, which is the text, so change 99 came
before change 105. Nothing showed until a test run wrote its hundredth change: the wants step then
could not find the decision it had just made among the "newest" ten. Order by the table's own column
(`activity_log.id`), or cast in the select under another name.

## The endpoint test fails once right after an edit

2026-09-28 and 2026-09-30. `supabase functions serve` reloads the function when a file under
`supabase/functions` changes, and a test run that starts a moment after an edit (or `deno fmt`) can
hit it halfway: steps fail at random, often `get_activity` missing rows it just wrote, and the next
run passes. Wait a few seconds after editing before running `endpoint_test.ts`, and rerun once before
chasing a failure that only an edit could explain.

## Deleting a merged base branch closes the pull request stacked on it

2026-09-29, M8-06 and M8-07. #10 was stacked on #9's branch. Merging #9 with `--delete-branch` did
not move #10 to `main`: GitHub closed it, and a closed pull request can't be retargeted, so it had to
be opened again as #11. Merge the lower one without deleting its branch, retarget the one above to
`main` (`gh pr edit <n> --base main`), and only then delete the branch.

## A dev build signs in to the cloud when GoalMaker.local.props names it

2026-09-28. `windows/GoalMaker.local.props` sets `GoalMakerSupabaseUrl` for release builds, and
`Directory.Build.props` imports it for every build, so a plain `dotnet build` dev app pointed at the
cloud project. The sign-in screen's footer names the backend; check it before signing in. For a local
check, build with `-p:GoalMakerSupabaseUrl=http://127.0.0.1:55321` and the local publishable key.

## A remote that never answers is a full resync every run

2026-09-23, dev builds without sign-in. The first local-only sync pushed to a remote that keeps
nothing and pulled from it too. Every write vanished about two seconds later, with no error: a table
that has never pulled has no watermark, `SyncRules.needsFullResync` says a table with no watermark
starts over, and starting over clears every row without a pending change before pulling, which then
brought nothing back. The outbox counter was the tell: seven pushes, empty tables. A local-only
`SyncEngine` now only pushes (`pulls = false`), and `SyncEngineTest` runs it twice over a row.

## A view model test waits on its state once

2026-09-23, the Made by switch. A Robolectric test that read `uiState.first { ... }`, changed the
view model's filter, and read `uiState.first { ... }` again hung until
`UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion`. The
first read passed; the second never saw the new state. The state comes from `stateIn(viewModelScope,
WhileSubscribed(...))`, whose work runs on the main looper, and most likely nothing runs that looper
while `runTest` waits a second time. Set everything up first, then wait once, and give each state its
own test, the way `GoalsViewModelTest` already did (`ProjectsViewModelTest`).

## A queued row must fill every column the server needs

2026-09-20, M5. Saving the composer's `+Project` and `?` set `item_type` and `board_column` on a new
task but left `priority` alone, so the outbox queued an explicit `null` and Postgres refused the push
with `23502: null value in column "priority" violates not-null constraint`. **A null in the payload
defeats the column's default**: the default only applies to a column the insert leaves out, and the
replica sends every column the synced-tables contract names.

This is now closed off rather than remembered: `contracts/schemas/synced-tables.json` says which
columns the server needs a value in, `tools/check_synced_tables.py` keeps that equal to the server
and lets the replica be laxer but never stricter, and `NewRows` refuses to create a row that misses
one, so the failure lands at the write with a message naming the column instead of at the push.
`RequiredColumnsTest` (and `RequiredColumnsTests`) walk every list and every synced table.

Closing it found a second live case the one-column fix had missed: a repeating task's next
occurrence carried no `item_type` or `priority` either, so every repeat would have been refused.

## Docker Desktop leaves a socket it cannot open again

Docker Desktop keeps its pipes and sockets in `%LOCALAPPDATA%\Docker\run`. Killed or crashed, it can
leave entries there that Windows lists but will not open: every delete and rename fails with "The
file cannot be accessed by the system", from Explorer, `del`, `rm` and .NET alike. The next start
then dies with

```
starting services: initializing Ingest server: listening on unix://...sailor-ingest.sock:
rename ...: The file cannot be accessed by the system.
```

and no amount of clearing helps, because the files are exactly what cannot be cleared. A directory,
though, can be renamed while its children cannot be touched, so leaving the whole folder behind lets
Docker Desktop make a clean one. `tools/fix-docker.ps1` does that and starts it again:

```powershell
powershell -ExecutionPolicy Bypass -File tools\fix-docker.ps1
```

The folders left behind stay until a restart of Windows clears the orphans, which is also the one
thing that always fixes this; the script removes the old ones once they have become deletable.

**Started from an agent, it happens every time.** 2026-09-28: Claude's desktop app is a packaged
app, and Docker Desktop started from its shell with `Start-Process` inherits the package's file
redirection. Its sockets then land in the package's own cache (`AppData\Local\Packages\Claude_...`),
where Windows can't open them even when they are brand new, so a clean `run` folder fails the same
way on the very next socket. Renames made from that shell only touch the redirected copy. Start it
outside the package, through Explorer, which `tools/fix-docker.ps1` now does:
`explorer.exe "C:\Program Files\Docker\Docker\Docker Desktop.exe"`. Never pick Reset to factory
defaults; it wipes images and volumes.

## The apps' tests never meet the server

The same bug: both apps' tests run against a test replica with a fake remote, so nothing there can
refuse a row. Only the connector's endpoint test and running the apps against the local stack reach
real constraints. After changing what a row carries, add a task in a running app against
`npx supabase start`, not only in the tests.

## Every migration needs its before and after fixtures

2026-09-20. `0012_task_moves.sql` shipped without `supabase/migration-tests/0012_before.sql`, and the
harness stops at the first migration that has none, so the whole chain went unchecked from M4 until
someone ran it. `python tools/supabase_migrations.py test` says exactly which file it wants; run it
with the migration, not later.

## WPF will not show a maximized window without activating it

2026-09-20, M6. `Show()` throws "Cannot show Window when ShowActivated is false and WindowState is
set to Maximized", so GoalMaker died at start-up whenever a window left maximized was launched with
`--no-activate`, which is how the dev guides and start-up scripts launch it. A window in that state
goes up normal and is maximized once it is on screen (`WindowShow.Plan`).

## A test that formats a date or a time must format it the way the code does

2026-09-20 and 2026-09-18. `"14,20 SEP"` and `"18:00"` are one machine's calendar and clock. A
culture that writes `Sept` or `6:00 PM` fails a test that hard-codes them. Build the expected text
with the same `CultureInfo.CurrentCulture` format the view model uses.

## CI that has never run proves nothing

The first push of this repository was 2026-09-20, so every check had been green only on one machine
until then. Two of the pitfalls above were waiting in `main` the whole time.

## A debug build is signed per machine

An Android debug APK is signed with `~/.android/debug.keystore`, which differs on every machine and
is not the release key on the backup drive. A phone that holds a debug build from another machine
cannot be updated in place: `adb uninstall com.goalmaker.app.debug` first, which clears that app's
replica, so check its outbox is empty before doing it.

## A lock drawn on the way out is still in the recent apps preview

2026-09-22, the phone's app lock. The lock is put up as the app leaves the screen rather than when
it comes back, which is right, but it is not enough on its own: Android takes the preview picture of
a task as the activity stops, before the new state has a frame to draw itself in, so the card in
recent apps kept showing the screen the owner had just been on. Unit tests all passed, because the
lock state was correct the whole time; only opening recents on a real phone showed it. An activity
that covers something asks for that picture not to be taken at all, with
`setRecentsScreenshotEnabled(false)` from API 33.

## WPF UI's text box keeps its underline with BorderThickness 0

2026-10-01, the chat UI polish. `ui:TextBox` draws its bottom line in a separate `AccentBorder` with a
fixed thickness, so `BorderThickness="0"` and a clear background still left a line inside the
composer's pill. Override its brush keys in the box's own resources instead
(`TextControlElevationBorderBrush`, `TextControlFocusedBorderBrush`, `ControlStrokeColorDefaultBrush`
and the `TextControlBackground*` brushes) and show focus on the container.

## A page snapshot taken out of its window ignores a theme switch

2026-10-01. `PageSnapshots` takes a window's content out to render it. Detached elements don't hear
the application's resources change, so applying another theme and saving again wrote the first
theme's picture under every name. Build the content again after each `ThemeApplier.Apply`.
