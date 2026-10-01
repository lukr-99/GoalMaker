# Goals

Goals give the plan a direction at every scale (spec, stories 27 to 35). The rules below are pinned
by [`contracts/vectors/goals.json`](../contracts/vectors/goals.json) and run by both apps and the
connector.

## Periods

A goal belongs to one period: a **year**, a **month**, a **week** or a **day**, named by the
period's first day (January 1, the first of the month, the Monday of the week, or the day). The
server refuses a period start that isn't the first day of its period.

## The cascade

A goal can serve a **parent** goal of a longer period ("3 runs this week" serves "Run a half
marathon this year"). The parent's horizon has to be longer (year, then month, then week, then day)
and its period has to overlap the child's, so a week that runs from September into October may
serve either month. Linking is optional. The apps check it; the server keeps the parent as a plain
id, so a sync that brings a child before its parent is never refused, and a parent that no longer
exists just leaves the goal without one. On screen a goal **feeds** its parent ("Feeds Run 100 km
in October").

## Progress

Each goal measures its progress one way:

| Mode | Value | Target |
|---|---|---|
| Done or not | 1 when the goal is done | 1 |
| Tasks | the tasks serving it that are done | the tasks serving it (dropped and deleted ones don't count) |
| Number | the amounts logged on it ("+5 km"; a negative amount corrects) | the goal's target |

The fraction (for the ring or bar) is the value over the target, from 0 to 1, and 0 when there is
nothing to count. A goal is **hit** when the fraction reaches 1, or when it was marked done; a dropped
goal is never hit. Check-ins of a habit linked to a numeric goal with the same unit (ignoring case
and spaces) count toward it too, like logged amounts ([habits](habits.md)).

## Pace

Each goal shows where it stands against how much of its period is gone: the days before today over
the period's days, 0 before it starts and 1 once it is over (so nothing is expected on a period's
first day, and today's goals expect nothing until the day is over).

- A dropped goal is **dropped** and a hit one **hit**, whatever the date.
- A goal counted by **tasks or a number** is **on track** while its fraction is at most 0.05 under
  that share. Otherwise it is **behind**, by what it misses to the share, rounded up ("Behind by
  6 km", "Behind by 2 tasks").
- A **done-or-not** goal, or a tasks goal with no tasks yet, is on track until 0.7 of its period is
  gone and then **needs you** (no amount).

Each period lists the goals that are behind first, then on track, then hit, keeping their own order
within each. The goals that are behind are the ones that "need you". The connector's `get_goals`
reads the same pace and order, and says what each goal feeds ([connector](connector.md)).

## The chain

Picking a goal lights its **chain**: the goal, every goal it feeds up the cascade and every goal
that feeds it, however deep. A missing parent ends the climb, and a loop (only possible through a
sync) is walked once.

## The quick log

A numeric goal's card has a quick log that adds the latest positive amount logged on it by hand
again ("+5 km"); with nothing logged yet it asks for an amount. A done-or-not goal's box ticks it.
A goal counted by tasks has no quick log, since its tasks count for it.

## Copying last period's goals

When a new week (or month) has no goals yet, the owner can copy last period's: every goal that
wasn't dropped comes back open, with the same title, emoji, mode, target and unit, keeping its
parent only when the parent's period still overlaps the new one.

## Tasks that serve a goal

A task can serve one goal, picked in its details from the open goals whose period hasn't ended.
Deleting a goal leaves its tasks without one. A repeating task's next occurrence keeps the goal
only while its day falls inside the goal's period ([repeating](repeating.md)).

## On screen

In the ladder view, the default, the Goals page has three parts, top to bottom (the owner's pick
from the habits, goals and add prototypes, 2026-10-01):

1. **Horizon rings.** One ring each for this year, month, week and today: the mean fraction of its
   goals, and how many are hit ("1 of 3 hit"). Under them, a line says how to light a chain and how
   many goals need you.
2. **The ladder,** from this year down to today. Each period shows its badge (Y, M, W, D), its dates,
   how many goals are hit and how much of it is gone ("Day 5 of 7"), then its goals as cards. A card
   has the goal's ring (or its box), name and the goal it feeds, the big number with "of 25 km" and
   a bar, its pace, and the quick log. Tapping a card lights its chain: the rest fade, the chain
   gets an accent edge, and a **Clear** chip puts it out (tapping the card again does too). Each
   period ends with Add a goal, and Copy last period's goals while it is empty.
3. **Next week,** for planning ahead, with **Copy this week's goals** while it is empty.

- **Android:** the flag in Today's top bar opens Goals. The ladder is one column with a rail down
  the left. Tapping a ring shows only that rung (next week stays with the week's); tapping it again
  shows all. A card's menu edits, logs any amount, marks done, drops, reopens or deletes. Hitting a
  goal throws confetti (skipped under reduce motion). Today ends with this week's goals, folded like
  the overdue tasks.
- **Windows:** Goals in the sidebar. The ladder is four columns of compact cards, and clicking a
  ring fades the other columns instead of hiding them. Each card is one button for the keyboard
  (Enter lights its chain); a right click or the menu key has the rest. The editor and the log
  panel open over the page, and a hit throws a lighter confetti burst. Today ends with this week's
  goals folded, and a task's details pick the goal it serves.

### List view

The header switches between the **Ladder** (above, the default) and a plain **List** (the owner's
ask for a very simple view, 2026-10-01). The list groups the goals by period in the same order: this
year, month, week and today, then next week. Each group has its name and dates, how many of its
goals are hit, and a button to add a goal to it. Each goal is one compact row: its box when it is
done or not, its name with where it stands ("12 of 50 km", "Not done yet") and a thin bar, its pace,
and the quick log. Within a group the goals that need you come first, as on the ladder. A tap on a
row opens the goal's editor. There are no rings, rails, chains or big cards: the rings are left out
because each group already says how many of its goals are hit, and the list is meant to be the
quiet view. Picking the list puts out a lit chain, and the list shows every period whatever ring
was picked. Each device remembers its own choice in its settings store, not synced.

- **Android:** an icon button in the top bar (a list icon in the ladder, a cards icon in the list).
- **Windows:** a small Ladder and List segment in the page header. The rows sit in one column up to
  820 pixels wide, with the pace and the quick log lined up on the right.

### Adding

The bottom bar adds goals in both views ([composer](composer.md#the-bottom-bar-on-every-list)): type
`Read 3 books this month` and send, with the period and the target previewed as chips; with nothing
typed its plus opens the goal editor for this week, and Ctrl+N on the PC opens it filled in with the
line. "Add a goal" under each period and the List view's per-group button stay, for adding straight to
one period.

The tree view that used to show the cascade is gone: the feeds line on each card and the lit chain
show how goals connect (story 34).

## Storage

`goals` and `goal_entries` are synced tables (Supabase migration 0009, replica migration 0004), and
`tasks.goal_id` links a task to the goal it serves. Deleting a goal takes it off its tasks.
