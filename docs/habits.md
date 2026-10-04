# Habits

Habits are the things the owner wants to keep doing, and the things they want to keep down (spec,
stories 36 to 42). The rules below are pinned by
[`contracts/vectors/habits.json`](../contracts/vectors/habits.json) and run by both apps and the
connector.

## Cadence and measure

A habit runs on one **cadence**:

| Cadence | Due | Its period |
|---|---|---|
| `daily` | every day | the day |
| `weekdays` | on the chosen weekdays | the day |
| `per_week` | any day, N times a week | the week, from its Monday |
| `per_month` | any day, N times a month | the month |

The chosen weekdays are stored as a bitmask, Monday 1, Tuesday 2, Wednesday 4 and so on to Sunday
64. N (`times`) is 1 to 7 for a week and 1 to 31 for a month.

It is measured one way:

| Measure | A day is met when |
|---|---|
| `check` | it was checked in (value 1) |
| `count` | the day's value reaches the target (8 glasses) |
| `amount` | the day's value reaches the target, in a unit (30 minutes, 5 km) |

## Something to reach, or a limit

A habit's number points one of two ways (`direction`):

| Direction | What the number means |
|---|---|
| `at_least` | something to reach: eight glasses, thirty minutes. The default. |
| `at_most` | a limit to stay under: two snacks a day, and a check habit means not once. |

A limit turns the day around. It is kept unless a check-in goes over the number, so a day nobody
logged anything on is a day kept, and going over misses the day the moment it happens, today
included. A pause or a skip is read before the day is judged, so a slip while paused costs nothing.
Only a daily or weekday habit can be a limit: "at most two on three days a week" says nothing anyone
can act on.

On screen a limit's ring fills with what has been had rather than what is left, never shows the done
check, and turns to the danger colour once the day is over the line, as does the line under the name
and the day on the heatmap. The heatmap reads the other way round for a limit: a clean day is full,
and the shade fades as the allowance is used.

## Check-ins

A day has at most one check-in per habit, holding the day's value; tapping again adds to it. Its id
is a name-based UUID of `checkin/<habit id>/<day>` (the reviews' namespace), so two devices checking
in on the same day land on the same row. A check-in can instead mark the day's period **skipped**
(sick, travelling), or **failed**.

## Failing a period

A skip excuses a period; a fail says it won't happen. When the owner knows today's run, glass or
week is lost, **Fail today** (this week, this month) in the habit's menu marks the period failed, so
it is missed at once, today included, rather than sitting open until midnight. The streak ends
there, the card stops asking (a failed habit is neither done nor left, so Today's "left" count and
the **all done** card don't wait for it), and the summary on the Habits page counts it against the
day. A failed day shows as missed in the week's dots and as empty on the heatmap; a limit's failed
day reads as over the line.

A failed check-in holds no value and no skip. Checking in, logging an amount or skipping takes the
fail back and starts the day from nothing, and **Undo the fail**, in the menu or on the check-in
button (a danger outline with a cross while failed), leaves the day empty again. A day before today
can fail too, from the calendar ([calendar](calendar.md)) or through the connector's `fail_habit`.

## Periods and streaks

Each period of a habit is in one state, the first that applies:

1. **none**: before the habit starts (`starts_on`), or a day the habit isn't due (a weekday outside
   its mask);
2. **met**: enough days in it are met (one for a day; N for a week or month);
3. **paused**: one of its days falls in a pause;
4. **skipped**: a check-in in it says skipped;
5. **missed** at once: a check-in in it says failed;
6. **open**: it hasn't ended yet (it holds today);
7. **missed**: otherwise.

So a weekly habit already met stays met when a later day of the week fails.

A limit is read in a different order, because it is kept by default: **paused**, then **skipped**,
then **missed** as soon as a check-in goes over the number or is failed, then **open** while the day
is still on, and **met** once the day is over with nothing over the line.

The **streak** counts met periods back from the current one: an open, paused, skipped or none period
is passed over without counting or breaking it, and the first missed one ends the streak. So a "3
times a week" streak counts weeks, and a holiday pause or a sick day costs nothing.

**Pauses** are ranges of days (`from`, and `until` or open-ended while the pause lasts); they stay
after the habit resumes, so old streaks still read right.

## Where a habit stands today

The cards on Today and the Habits page read one **standing** per habit, the first that applies
(`standings` in the vectors):

1. **none**: archived, not started yet, or not due today (a weekday outside its mask);
2. **paused**: a pause covers today;
3. **skipped**: a check-in in the period holding today says skipped;
4. **failed**: a check-in in the period holding today says failed;
5. **limit**: a limit is never done and never left, so Snacks never reads as "not done";
6. **done**: today's ring is full, or a weekly or monthly habit was checked in today (one run of
   "3 times a week" is today's part, even while the week still needs more);
7. **left**: otherwise.

Today's "left" count counts the left ones, **Hide done** hides the done ones, and Today shows its
short **all done** card when none is left and at least one is done (`allDone`). A skipped habit stays
in place with a dashed button that undoes the skip.

The Habits page groups habits into **Every day** (daily and chosen weekdays), **Weekly** (N times a
week or a month) and **Limits** (`groups`). Each card shows **the week's dots**, the last seven days
up to today (`dots`): met, missed, skipped, paused, over a limit, today still open, or nothing on a
day the habit isn't due. A weekly habit's day without a check-in misses nothing, so it shows
nothing; a limit's clean day is met.

## On screen

- **A habit card** (the habits prototype, option B, chosen 2026-10-01) shows the emoji, the name
  with its streak as a flame and a number, where today stands, pips for a small count or the days a
  weekly habit needs (a limit's pips past the line turn to the danger colour) or a bar for an amount,
  and the week's dots. Its **check-in button** checks a check habit (and takes it back), adds one to
  a count ("+1"), and asks for the value of an amount; once today's part is done it fills with the
  accent and turns round, while the habit is skipped it is a dashed outline whose click undoes
  the skip, and while it is failed it is a danger outline with a cross whose click undoes the fail. Every check-in writes the day's one check-in, so three then five is eight. A streak
  reaching 7, 14, 30, 50, 100, 200, 365, 500 or 1000 periods gets confetti, skipped under reduce
  motion.
- **The menu** holds the rest: check in or add one, log an amount, skip today (this week, this
  month) or undo the skip, fail today (this week, this month) or undo the fail, clear today, pause or resume, edit, archive and delete; on Today also
  Open Habits. On the phone a long press on the card or its menu button opens it as a sheet, and a
  screen reader gets Skip and More as actions, so nothing needs the long press. On Windows the
  card's menu button, a right click or the menu key open it.
- **The heatmap** gives each day a value: nothing before the start or on a day that isn't due,
  paused, skipped, over a limit, or the day's value against its target from 0 to 1 (a check is 0 or
  1; a limit's day is what is left of the allowance). It runs by
  weeks, Monday at the top, from the habit's first week and at most 26 weeks back, and shows the last
  weeks that fit the width.
- **Today** holds the habits on Today as cards (design spec, Today). On the phone they sit behind
  a **Tasks / Habits** switch under the date (option C), each half with what is still open; the
  habits half says how many are left, has **Hide done** and shows the short **all done** card, and
  the tasks half points at the habits left with a line that switches over. The switch is kept while
  the app runs and starts on the tasks. On Windows the cards sit in a **panel beside the tasks**, one
  column, with the same count, Hide done and all done card; where the window is narrow, and in the
  Today mini window, the panel sits under the tasks.
- **The Habits screen** (option B) starts with a summary card on the hero colors: today's count of
  done habits against the ones that ask something of today, a ring of how far the day has got (a
  habit still to do counts its ring), and the longest streak. Then come the groups, **Every day**,
  **Weekly** and **Limits**, of full cards that add how often, the goal served, the heatmap and the
  Not on Today mark; Hide done (a group it empties says "All done."); and the archived habits folded
  at the end. On Windows the cards sit two to a row where there is room.
- **Adding** is the bottom bar's job ([composer](composer.md#the-bottom-bar-on-every-list)): type
  `Swim 2 times a week 40 min` and send, with how often and how much previewed as chips; with nothing
  typed its plus opens the habit form, and a line that leaves no name opens the form filled in. A
  limit, an emoji and the goal it serves are set in the form.
- A habit can be **archived**: it leaves Today and the list, keeping its history, and can come back.

## Keeping a habit off Today

Some habits are worth tracking but not worth a ring on Today every day. The habit form's **Show on
Today** switch (on by default) keeps one off Today when it is off:

- It leaves Today's habits, the Today mini window and the phone's Habits widget, and Today's
  "left" count no longer waits for it.
- It stays on the Habits screen and the Habits mini window, its card marked **Not on Today**, and
  is checked in there as before.
- It keeps its streak and its map, and still counts wherever habits are counted: the Places hub's
  Habits tile, Stats, reviews and the Letter's digest, and the goal it serves.
- It is not a pause. A pause stops the cadence, so its days neither count nor break the streak; a
  habit kept off Today still asks for its days.

Which habits a day holds is pinned by the `onToday` group of
[`contracts/vectors/habits.json`](../contracts/vectors/habits.json): a habit is **due today** when it
is not archived, has started, is due that day and is not paused, and it is **on Today** when it is
due today and not kept off. The Places hub counts the first; Today and the widget show the second.
Through the connector, `get_today` lists the habits on Today with their standings and the count of
the ones left, and only counts the ones kept off it; `get_habits` groups them like the Habits page
and says "not on Today" beside them ([connector](connector.md)); and `add_habit` and `update_habit` take
`show_on_today`.

## Goals

A habit can serve a goal. When the goal counts a number and the habit's unit is the goal's (ignoring
case and spaces), the habit's check-ins in the goal's period add to it like logged amounts (story 32,
[goals](goals.md)).

## Reminders

The habit form's **Remind me** sets a time of day a habit reminds at, on the days it is still left,
with Check in (+1 for a count, Log for an amount) and Skip on the notification; see
[reminders](reminders.md#habit-reminders).

## Storage

`habits`, `habit_checkins` and `habit_pauses` are synced tables (Supabase migration 0010, replica
migration 0005). `habit_checkins.failed` came with Supabase migration 0021 (replica 0015), false by
default; a trigger clears it whenever a check-in holds a value or a skip, so an app from before it,
which leaves the column out, still takes a fail back by checking in. `direction` came with Supabase migration 0014 (replica 0009) and `show_on_today`
with Supabase migration 0020 (replica 0014); both have defaults, so a habit from before reads as it
did. `remind_at` came with Supabase migration 0022 (replica 0016), null for no reminder. Deleting a habit takes its check-ins and pauses with it; deleting its goal leaves the
habit without one.
