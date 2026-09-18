# M2-10: Tray Today flyout and the quick-add hotkey

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- A Today flyout from the tray icon; a global hotkey (default Win+Alt+Space, changeable) opening a
  quick-add composer that doesn't steal the app's window; `--open` for the new lists.

## Acceptance criteria
- Capture from any app in two keystrokes plus the text; the item syncs to the phone.

## Result
- A left click on the tray icon shows the Today flyout: today's open tasks in the list's order,
  overdue first, with done boxes, a count past eight, "Add a task" and "Open GoalMaker"; a double
  click opens GoalMaker, and the tray menu can add a task too.
- The global shortcut (NHotkey, default Win+Alt+Space) opens a small quick-add box above whatever
  app is in front, with the keyboard in it and without bringing GoalMaker's window up. A line
  without a day lands in the Inbox; Enter saves and closes, Escape or a click elsewhere closes, and
  the keyboard goes back to the app it came from. Settings shows the shortcut, records a new one
  from the keys pressed, and says when another app already owns it.
- `--open today|tomorrow|inbox|plan|settings` already covered the lists from M2-06.
- Checked live: the app registers the shortcut, the box appears with the keyboard in its text box,
  closing it keeps the app running, and quitting frees the shortcut.

## Notes
- PowerToys Command Palette also defaults to Win+Alt+Space. With it running, GoalMaker can't take
  the default and Settings says so; pick another shortcut there, or change the palette's.
- "The item syncs to the phone" rides on the normal outbox and couldn't be checked end to end
  without a backend (no Docker here).
