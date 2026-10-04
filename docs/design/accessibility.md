# Accessibility

What each screen of both apps does for a screen reader, the keyboard, large text, contrast and
reduced motion, what the M6-05 pass fixed, and what is still open. The bar comes from the
[design spec](spec.md) ("Readable first") and the [questionnaire](questionnaire.md) (D16): WCAG AA
contrast in every theme, layouts that follow the system text size and are tested at the largest
setting, and a reduce-motion switch every animation obeys.

Last pass: 2026-10-03. Fixed now means fixed in that pass.

## What keeps it so

| Check | What it holds | Where |
|---|---|---|
| Names | Every Windows control that does something has something to read (an automation name, its content, a header, a placeholder or text inside); every Android `IconButton` says what it does | `tools/check_accessibility.py`, in CI |
| Reach on Android | A `bottomBar` built from a Row or a Column keeps clear of the system navigation bar | `tools/check_accessibility.py` |
| Tab order on Windows | No `DockPanel` puts a control docked Right or Bottom before one it is drawn after, unless it sets `KeyboardNavigation.TabNavigation` and gives `TabIndex` in reading order | `tools/check_accessibility.py` |
| Contrast | Text, muted text, accents and the Settings highlight at 4.5:1 or better in all four themes, light and dark | `tools/check_design_tokens.py` |
| Windows keyboard | WPF's own Tab walk through the composer, the tray flyout, the mini windows and Today's habit panel, the names of the stops, where each window starts the keyboard, Esc | `KeyboardReachTests` (`TabOrder`, `WpfApp`) |
| Windows text size | Windows' text size clamped to 100 to 225%, the windows grown with it, and no control outside a scrolling list past the edge of a mini window at its smallest, the flyout or the quick-add box | `LargestTextTests` |
| Android semantics and text size | The composer's chips, a task row, the habit card and its check-in, a calendar cell, settings rows and the navigation bar as TalkBack gets them, and laid out at font scale 2 on a 360 by 640 dp phone | `*AccessibilityTest` in `app/src/test/.../ui/` (Robolectric with Compose, `TestTheme`, `assertLaidOutIn`) |

How the apps follow the system text size differs. Android does it through `sp` and Compose: text
grows and layouts reflow, while icons and touch targets keep their `dp` size. WPF ignores Windows'
text size, so `Theming/TextScale` reads it (`UISettings.TextScaleFactor`) and scales each window's
content as a whole: text, icons and spacing grow together and the page reflows in the room left.
Menus, tooltips and drop-down lists open in their own popups and keep the normal size.

## Contrast and motion, both apps

Contrast comes from the theme tokens, so it holds on every screen that draws with them, which is
all of them. Two things no check measures: a muted composer chip (an unknown command) draws at 70%
opacity, and a disabled control at 45%. WCAG exempts disabled controls; the muted chip is left.

Reduce motion follows the system and the in-app switch. It turns the moves between screens into
fades (`NavTransitions`), skips confetti on Goals and Habits on both apps, makes the habit ring and
the check-in fill at once, cross-fades the composer's plus and arrow, and skips the launch logo and
the Go to box's grow on Windows. Left: on Android the composer pill and the chat thread still grow
with `animateContentSize`, and Today's habit cards still slide into place with `animateItem`, under
reduce motion. Both are short, but they are movement.

## Android

### Today, Tomorrow and Inbox

- Names. Every action has a name (checked by the script).
- Order. The Scaffold reads top bar, list, then the bottom bar, and Tab takes the same order.
- Large text. The filter row scrolls sideways, the Today switch takes a minimum height. Checked by
  reading.
- Left. Nothing known.

### Task row

- Names. Fixed now: the row is one TalkBack item (title, details, reminder and time) and carries
  Delete and Remind as its actions. Before, the actions sat on a node TalkBack never stopped on. The
  done box is named "Call the bank done" and the reminder icon "Reminder set".
- Order. Done box, then the row.
- Large text. Tested at font scale 2: the box, a three-line title and the time stay on screen.

### Composer and its chips

- Names. Fixed now: a removable chip reads "Tomorrow, Remove, button" instead of "Remove
  Tomorrow, Tomorrow, not selected, checkbox" (an assist chip in the input chip's colors replaces the
  input chip). A read-only chip on Wants, Habits and Goals is one item, and the warning one says
  "Warning".
- Order. Chips, then the line, then the round button, for TalkBack and for Tab (tested). The chips
  sit above the line, so no traversal index was needed.
- Large text. Tested at font scale 2: every chip, the line and the button stay on screen.
- Left. The " · " inside a chip's label may be spoken as "middle dot", depending on TalkBack's
  punctuation setting. Not heard on a device.

### Habits and the habit card

- Names. The check-in button has a name, the button role, a click and a state ("1 of 3 today").
  Fixed now: the card reads as one item (the name column was a second stop), and the check-in's "+1"
  is no longer read after "Add one".
- Large text. Fixed now: the check-in takes a minimum size rather than a fixed one, the name gets two
  lines, the emoji on its tile keeps its size, and the habit sheet scrolls so its last choices stay
  in reach. Tested at font scale 2.

### Goals

- Large text. Fixed now: the horizon badge takes a minimum size. Checked by reading.
- Left. In a goal row the pace chip and the quick-log button take their full width, so at font
  scale 2 the title gets narrow. Nothing leaves the screen.

### Places

- Names. Fixed now: the Places tab says how many things wait there ("Places, 3 waiting"); the badge
  was never read, because Material clears an item's icon semantics when it has a label.
- Large text. Fixed now: a tile's title gets two lines. Tiles take a minimum height. Checked by
  reading.

### Calendar

- Names. Fixed now: a day cell says "Saturday 3 October, today, 2 planned, 1 due, 1 reminder" (or
  "nothing on it") instead of a bare number. Tested.
- Large text. Fixed now: a cell keeps its shape as a minimum and grows taller when the text needs
  it; a week's cells share one height. Tested at font scale 2 on a 320 dp wide phone, where the old
  fixed shape cut off the bar.
- Left. Moving a task to another day is drag and drop only; TalkBack and the keyboard move it from
  the task's details instead.

### Task details, Plan tomorrow, Wants, Projects, Archive, Stats, Tally, Reviews

- Names checked by the script. Each screen scrolls, and Plan tomorrow's decision chips wrap. Checked
  by reading; no dedicated test.

### Settings

- Names. A switch row is one switch. Tested.
- Large text. Fixed now: a row's control takes at most 60% of the row, so a long button wraps
  instead of squeezing the title to a letter a line (tested), and the jump chips lost their fixed
  32 dp height (checked by reading).

### Dialogs and sheets

- Large text. Fixed now: the reminder sheet scrolls (tested). The new task and want sheets already
  scrolled. The confirm dialog and the time picker are Material's own. Checked by reading.

### Navigation bar and top bars

- Tested: the tabs, which one is selected, the Places count, and the labels inside the bar at font
  scale 2. The top bars' titles shrink to fit and their actions are named. Checked by reading.

### Sign-in

- Large text. Fixed now: the main button takes a minimum height of 56 dp instead of a fixed one. The
  screen scrolls. Checked by reading.

## Windows

### Main window and sidebar

- Names. Every sidebar item, Go to, the pin toggle and the title bar's buttons are named (WPF UI's
  own and the script).
- Keyboard. Ctrl+K opens Go to: type, arrows, Enter, Esc. All places opens on Enter and folds with
  Right and Left. A place pins or unpins from the menu key or Shift+F10. Ctrl+N opens the page's form.
- Large text. Fixed now: everything under the title bar follows Windows' text size; the title bar
  keeps the size of every other window's, and the Go to box never gets wider than the window.
- Left. At 225% the sidebar alone is 540 pixels wide, so a window under about 1400 pixels leaves the
  page cramped. Maximize it, or fold the sidebar to icons with its toggle.

### Today, Tomorrow and Inbox

- Names. A task's done box is named by its title with its state as help text; Delete and Open are
  named; the menu key on a row offers its reminders.
- Order. Fixed now: a row reads done box, Delete, Open (it was Open, Delete, done box), and the
  toolbar reads the pickers, the sync state, the mini window button and Plan tomorrow (it was the
  other way round). Tested.
- Keyboard. Fixed now: when the new task form closes, the keyboard goes back where it was.
- Large text. Fixed now: where the list is narrow the toolbar's right-hand group moves under the
  pickers, the pickers wrap, and the day's summary wraps.

### Composer, on every page

- Order. Fixed now: chips' remove buttons, chat switch, line, round button, as they show. It was
  chips, round button, switch, line. Tested.
- Keyboard. Enter adds or sends, Ctrl+N opens the form, Ctrl+Shift+Space flips the chat. Fixed now:
  Esc clears the line, and on an empty line it is left to the window, so a second Esc closes a mini
  window. Tested.

### Quick-add box

- Keyboard. The keyboard lands in the line, Esc or a click elsewhere closes it, and the app that had
  the keyboard gets it back. Tab goes round the switch, the line and the round button. Fixed now:
  Narrator reads the hint (what Enter does, the chat shortcut) with the line.
- Large text. Fixed now: the box scales with Windows' text size and widens as far as the screen
  allows. Tested at 225% on a 1280 pixel wide screen.

### Tray icon, menu and flyout

- Keyboard. Fixed now: with the icon chosen in the notification area (Win+B, then the arrows),
  Enter or Space shows the Today flyout and Shift+F10 or the menu key opens the menu. Before, the
  keyboard could reach the icon but not open either. The flyout puts the keyboard on its first task,
  Tab goes round it without leaving, and Esc closes it and hands the keyboard back to the
  notification area.
- Order. Fixed now: Add a task, then Open GoalMaker, as they show. Tested.
- Large text. Fixed now: the flyout scales with Windows' text size. Tested.
- Left. Opening the icon by keyboard and Esc back to the notification area use H.NotifyIcon's
  keyboard events and the shell's NIM_SETFOCUS. Checked by reading, not by hand with Narrator.

### Mini windows

- Names. Keep on top, Open GoalMaker and Close are named. Fixed now: the pin says "Stop keeping on
  top" while pinned.
- Keyboard. Fixed now: Today's window starts the keyboard on the first task (the composer when
  Today is empty), Habits on the first check-in. Tab reads the title bar, then the content. Esc
  closes the window, but only after the composer has cleared its line or a picker has closed;
  before, Esc closed the window from inside the composer. Closing gives the keyboard back to the
  window that had it. Tested.
- Large text. Fixed now: the window grows with the text within the screen, drops the list's big
  headline (the title bar names it) and keeps a narrower margin. Its smallest size is now 260 by 320
  pixels at normal text, grown with the text: at the old 220 the composer fell below the edge even at
  normal text. Tested at 100% and 225%, with and without a filter on.
- Left. A mini window has no caption, so moving and resizing it need the mouse.

### Habits page and habit cards

- Names. The check-in is named for what it does, with where the day stands as help text; the menu
  button and the menu key open the habit's menu.
- Order. Fixed now: Today's habit panel reads Hide done before Open (tested), and the editor's
  reminder row reads the time before the switch.
- Focus. The check-in draws its own focus ring.

### Task details, Areas and tags, Reviews, the review, Wants, Archive, Projects, Calendar

- Order. Fixed now, all by `TabIndex` in a local tab order: a step reads done box, title, then its
  buttons; a new tag, step, area or tag box comes before its Add button; Restore before Delete on an
  archived area; a review's Open before Delete; a guided review's step before Back and Next; a want's
  Buy and Drop before Edit; an archived task's Open before Reopen; a board column's Add before Fold;
  the calendar's Week and Month before Earlier, Today and Later; the new project item window's Add
  another before Cancel and Add.
- Calendar. Fixed now: a day cell says its date, whether it is today or open, and what is on it, as
  on the phone. Tested.
- Left. Moving a task between calendar days is drag and drop only; by keyboard the task's details
  set its day instead.

### Settings, Stats, Tally, Activity, Places, Goals

- Names checked by the script; Tab order held by the DockPanel rule. Settings is the tray kit's
  page. Not walked by keyboard one control at a time.

### Dialogs

- Left. The new project item window and the tray kit's windows (confirm, message, startup failure)
  keep their normal size at large Windows text. They wrap their text and are small.

## Not checked on a device

The pass checked semantics, Tab order and layout in tests and by reading. Nobody listened to
TalkBack or Narrator read these screens on a real phone and PC. That is the next step whenever
something here sounds wrong.
