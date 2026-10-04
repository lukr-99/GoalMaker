# Life goals

> M9 (`.scratch/m9-life-goals/`): the data and rules (M9-01) and the place on both apps (M9-02,
> M9-03) are built. The why reminder, the widget and the connector tools follow.

A **life goal** is something the owner wants in their life in the long run ("Own an Audi R8"),
written down with **why** it matters, an optional **by** date and pictures of it (spec, stories 114
to 119). Life goals are the reason behind the daily plan. Now and then a **why reminder** shows one of
them, and a widget on the phone cycles through their pictures.

A life goal is not a [goal](goals.md). It has no horizon, no progress mode and no pace, and it is not
part of the goal cascade: a year goal can't feed it. The owner picked a separate list over a "life"
rung on the ladder (2026-10-04).

## A life goal

| Field | Notes |
|---|---|
| title | Required |
| why | Required: the reason is the point, and the why reminder shows it |
| by | Optional date. The editor offers In 5, 10 and 20 years, or a picked day |
| area | Optional |
| status | `open`, `achieved` or `dropped`, with the time it changed |
| position | The owner's order, set with Move up and Move down |
| made by | Owner or Claude, like tasks |
| pictures | None or more, in their own order |

`life_goals` and `life_goal_pictures` are synced tables (Supabase migration 0023, replica migration
0017) with the usual row security, tombstones, activity log and undo, and both are in the backup.

## Time left

Under each open life goal with a by date, a line says how far off it is, from the planning day to the
by date:

- a year or more: whole years ("10 years left", "1 year left")
- under a year: whole months ("8 months left")
- under a month: days ("12 days left", "1 day left")
- the by date itself: "Today"
- after it: "Past its date"

Whole years and months count like birthdays: a month is whole once the day of the month is reached.
From October 4, 2026, the date October 3, 2036 is 9 years left and October 4, 2036 is 10, and from
January 31 to February 28 is 28 days, not a month. Pinned by `timeLeft` in [`contracts/vectors/life-goals.json`](../contracts/vectors/life-goals.json).

## Order

Open life goals come first, in the owner's order (position, then when they were made, then id). Then
achieved and dropped ones, the most recent first. Pinned by `order` in the vectors.

## Pictures

Each picture is a row in `life_goal_pictures` and a file in the private Storage bucket
`life-goal-pictures`, at `<owner id>/<picture id>.jpg` (ADR 0018). Before uploading, an app shrinks a
picture to at most 1600 pixels on its longest side and saves it as a JPEG, so a file stays well under
the bucket's 2 MB limit.

- **Adding.** The app writes the file to its picture cache and the row to the replica at once, so the
  picture shows offline. The file uploads when the app is online; until then the other device shows
  the row as a placeholder.
- **Showing.** A device that lacks a file downloads it once and keeps it in its cache.
- **Removing.** The row gets a tombstone like any synced row, and the app removes the file from the
  bucket. Deleting a life goal removes its pictures.
- **Backup.** The JSON backup holds the rows, not the files ([backup](backup.md)). A restore on a new
  device downloads the files from the bucket while they are there.

## The why reminder

A notification now and then that shows one open life goal, its why, its time left and its first
picture. Tapping it opens the Life goals place on that goal. It is a device setting, like the review
reminders: **Off**, **Weekly** (the default), **Every 3 days** or **Daily**. It needs at least one open
life goal.

The moment is random but the same on both devices, so it is worked out, not stored:

1. The frequency splits time into **periods**: days, blocks of 3 days counted from January 1, 1970,
   or weeks from Monday.
2. A **seed** is the 32-bit FNV-1a hash of the text `<frequency>:<period's first day>` (for example
   `weekly:2026-09-28`).
3. The day is the period's first day plus seed modulo the period's length in days. The time is 10:00
   plus (seed divided by the period's length) modulo 600 minutes, so it falls between 10:00 and
   19:59. If that time is inside quiet hours, it moves to when they end.
4. The life goal is the open one at the period's number modulo how many are open, in their order.
   So each period shows the next one.

Each look (the same look that arms task reminders) shows the latest moment since the last look, if
there was one. So a device that was off at the moment shows it when it is back, but only the latest
one. The alarm is armed for the next moment. Pinned by `fnv1a`, `whyMoment`, `whyGoal`, `whyDue` and
`whyNext` in the vectors.

## On screen

**Life goals** is a place on both apps, after Goals, with a tile on the Places hub ("2 life goals").

- Each open life goal is a card: its pictures to swipe or click through (or the accent with its first
  letter when it has none), the title, the time left, and the why in full. A life goal Claude added
  says "by Claude". Achieved and dropped life goals fold under the open ones.
- The editor has the title, the why, the by date (No date, In 5, 10 or 20 years, or a picked day)
  and the pictures. Pictures are added and removed there and kept, with the rest, when the editor
  saves; they show in the order they were added. A life goal keeps its area, but the editor does not
  set one yet.
- A card's menu edits it, marks it achieved, drops it, moves it up or down (the owner's order),
  reopens a closed one, or deletes it after asking. Achieving, dropping and deleting offer an undo.
- **Android:** a picture comes from the system photo picker (up to six at once). The widget is below.
- **Windows:** a picture comes from a file picker, or is dropped onto the editor, and is shrunk on
  the PC the same way. The by date is a list (No date, In 5, 10 or 20 years, Pick a day) with a date
  picker. Each card is one button for the keyboard: Enter edits it, the menu key or a right click
  opens its menu, and Left and Right click through its pictures. Ctrl+N adds a life goal and
  Ctrl+Enter saves the editor. Delete asks inside the page.

## The widget (Android)

A home screen widget that shows one picture of an open life goal at a time, with its title and time
left, and moves to the next picture every 30 minutes (through all pictures of all open life goals,
in order). A life goal without pictures shows its title and why on the area's color. Tapping it opens
that life goal. With no open life goals, it says so and opens the place.

## Through the connector

`get_life_goals`, `add_life_goal` and `update_life_goal` (title, why, by, area, status). The answer
says how many pictures a life goal has; pictures themselves are added in the apps
([connector](connector.md)).
