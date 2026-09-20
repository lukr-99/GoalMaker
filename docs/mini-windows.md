# Mini windows

Two small windows keep Today and Habits on the desktop while you work on something else (spec,
stories 80, 82 and 83). They are Windows only; the phone has widgets instead
([widgets](widgets.md)).

## What they show

| Window | What is in it |
|---|---|
| Today | Today exactly as the page shows it: the sections, the overdue fold and the composer, so a task ticks off and a new one goes in without opening GoalMaker. |
| Habits | Today's habits as short rows: the ring, the name, where the day stands and the streak. Clicking a ring checks the habit in. |

Both run the same view models as the main window, so a tick here is the tick there, and both follow
the theme as it changes. A habit that measures an amount opens the main window's log panel, which is
the only place an amount can be typed.

## Opening one

- The tray menu: **Today mini window** and **Habits mini window**.
- Settings, **Mini windows**.
- `GoalMaker.exe --mini today` or `--mini habits`.
- A `goalmaker://mini/today` or `goalmaker://mini/habits` link.

A launch that only asks for a mini window leaves the main window where it was, so a shortcut can put
Today on the desktop without the whole app coming up. Launching GoalMaker again never makes a second
copy: the running instance takes the switches over a named pipe and answers them (story 83).

## What they remember

Each window keeps its own place, its size and whether it was pinned, under its own name in the
settings file. **Pin** keeps the window above other apps. A place saved on a monitor that is no
longer there is ignored, so a window never opens where it cannot be grabbed. Closing a mini window
leaves GoalMaker running; only Quit ends it.
