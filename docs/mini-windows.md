# Mini windows

Two small windows keep Today and Habits on the desktop while you work on something else (spec,
stories 80, 82 and 83). They are Windows only; the phone has widgets instead
([widgets](widgets.md)).

## What they show

| Window | What is in it |
|---|---|
| Today | Today exactly as the page shows it: the sections, the overdue fold and the composer, so a task ticks off and a new one goes in without opening GoalMaker. The habits panel sits under the tasks, since the window is too narrow to have it beside them. |
| Habits | The Habits page's habits as short rows, the ones kept off Today too: the ring, the name, where the day stands and the streak. Clicking a ring checks the habit in. |

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

## By keyboard and at large text

A mini window opens with the keyboard on Today's first task or the first habit's check-in (on the
composer when Today is empty). Tab goes round the title bar's Keep on top, Open GoalMaker and Close,
then the window's content in the order it reads; Esc closes the window once the composer's line is
empty and no picker is open, and the window that had the keyboard before gets it back.

Both follow Windows' text size (Settings, Accessibility, Text size): the content scales as a whole
and the window grows with it, as far as the screen allows. A mini window leaves out the list's big
headline, since its title bar names the list, and gets no smaller than Today's header, a task and
the composer need, so nothing is ever below its edge.

## What they remember

Each window keeps its own place, its size and whether it was pinned, under its own name in the
settings file. **Pin** keeps the window above other apps. A place saved on a monitor that is no
longer there is ignored, so a window never opens where it cannot be grabbed. Closing a mini window
leaves GoalMaker running; only Quit ends it.
