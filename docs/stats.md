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

**Project work.** Each week also counts how many of its tasks were project work: items of a project
that is still there ([projects](projects.md)). Everything else is other work, the items of a
deleted project included, since deleting a project leaves its items behind as plain tasks. The
window's project work is the hero's second line ("8 on projects"), and **By project** gives each
project's finished items over the same twelve weeks, most first, leaving out the projects that
finished nothing; two projects with the same count keep the project list's order. The screen shows
the first five and says how many more there are.

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

Once any of the twelve weeks had project work, each tasks bar stacks it: the project work in the
full accent at the bottom, the other work in the faded accent above it, with a legend under the
chart ("Projects 8", "Other 7"), and the By project block follows the chart with a bar per project
against the busiest one. Each week's bar names itself for a screen reader (the Windows tooltip says
the same): "Week of 14 Sep: 5 done, 4 on projects". Without project work the chart looks as it
always did. The Places tile keeps its one number ("5 done this week") rather than splitting it: a
tile is a glance, and the split is one tap away on this screen.
