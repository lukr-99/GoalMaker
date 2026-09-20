# Stats

The stats screen is the long view (spec, stories 64 and 67): how much gets finished week by week,
how the month's goals end up, whether the habits hold, and how the weeks have felt. It reads what is
already synced, so it needs no table of its own, and both apps work the numbers out the same way
([`contracts/vectors/stats.json`](../contracts/vectors/stats.json), `StatsRules`).

## The numbers

**Tasks a week.** The last twelve weeks, oldest first, the last of them the week holding today. A
week runs Monday to Sunday and a task counts on the day of its `completed_at`; deleted and unfinished
tasks never count. The hero line is the total, what it works out at a week, and the fullest week
(the earliest of them when two tie).

**Goals a month.** The last six months, oldest first. A month holds every month goal whose period
starts in it that is not deleted or dropped, and a goal counts as hit by the same rule the goals
screen uses ([goals](goals.md)), so a numeric goal a habit feeds counts what the habit logged
([habits](habits.md)).

**Habits.** Each habit that is not archived, over the same twelve weeks: the periods it asked
something of (the ones that are not `none`), how many were met, the run going now counted back from
today, and the longest run inside the window. A missed period ends a run; paused, skipped and open
ones are passed over, exactly as a streak is counted. The hero number is the periods met against the
periods asked, over all the habits together.

**Mood and energy.** The ratings of the last twelve weekly reviews that rated either, oldest first
(story 64). A review that rated only one of the two still shows, with a gap where the other is.

## In the apps

Android reaches it from the overflow menu on Today, Windows from the sidebar or `--open stats`. Both
draw the same four blocks: the tasks bars with today's week last, the goals bars with the share hit,
a row per habit with its rate and streak, and the mood and energy lines. When there is nothing to
show yet the screen says so rather than drawing empty charts.
