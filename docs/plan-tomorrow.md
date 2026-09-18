# Plan tomorrow

The evening ritual (spec stories 22 to 24): decide on everything left over from today, then set up
tomorrow. Nothing rolls over by itself. The rules below are pinned by
[`contracts/vectors/plan.json`](../contracts/vectors/plan.json) and run by both apps.

## Starting it

The **Plan tomorrow** button on Today and Tomorrow, the composer's `/plan`, and on Windows
`--open plan` or `goalmaker://open/plan`. The evening reminder (M2-09) opens it too. The planning
day ([lists](lists.md)) is read once when the ritual starts, so "today" and "tomorrow" stay put
even if the day's start hour passes halfway through.

## Step 1: Today

Every open task planned for today or earlier, oldest day first, then time (untimed last),
creation, id. Each one gets a decision:

| Decision | What it does |
|---|---|
| Tomorrow | plans it for tomorrow, keeping its time |
| Date | plans it for a day after tomorrow, picked in a calendar, keeping its time |
| Done | completes it (it got done, it just wasn't ticked) |
| Drop | drops it: it stays in the history but leaves every list |

The spec names three decisions; **Done** is the fourth because tasks often get done without being
ticked, and leaving the ritual to tick them is friction.

Decisions are saved at once and sync like any other change, so closing the ritual halfway keeps
what was decided. A decision can be changed until the ritual ends by picking another one; choosing
Tomorrow or Date after Done or Drop reopens the task.

What a task shows follows from its state, so a change made on another device mid-ritual shows up
here too:

| Task | Shows |
|---|---|
| open, planned today or earlier | undecided |
| open, planned tomorrow | Tomorrow |
| open, planned after tomorrow | Date (the day) |
| open, no day (changed elsewhere) | decided, "no day" |
| done | Done |
| dropped | Drop |

Tasks deleted elsewhere leave the step; tasks that become due today elsewhere join it at the end.
**Next** is available once nothing is undecided; with nothing left over, the step says so and
Next is available at once.

## Step 2: Tomorrow

Tomorrow's tasks by time (untimed last), then creation, id; unlike the Tomorrow list, flagging
doesn't move a row to the top, so rows stay put while you pick. Then the composer (a line lands on
tomorrow unless it names a day), and the Inbox folded below with a **Tomorrow** action per task.

Flag up to **3 top priorities**. Tasks already flagged count; once three are flagged, the other
flags are disabled until one is cleared. If more than three are flagged already (typed with `!`),
they stay until cleared.

## Done

A summary card: tomorrow's task count as the big number, how many top priorities it has, and how
today's tasks were decided. Back to Today from there.

## Notes

- **Repeating tasks** are decided like the others; Done and Drop move the series on to its next
  occurrence, and changing the decision back takes that occurrence back ([repeating](repeating.md)).
- The evening reminder (M2-09) needs to know whether today's ritual already happened on either
  device, which takes a synced record of it; that arrives with the reminder.
