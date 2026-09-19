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
exists just leaves the goal without one.

## Progress

Each goal measures its progress one way:

| Mode | Value | Target |
|---|---|---|
| Done or not | 1 when the goal is done | 1 |
| Tasks | the tasks serving it that are done | the tasks serving it (dropped and deleted ones don't count) |
| Number | the amounts logged on it ("+5 km"; a negative amount corrects) | the goal's target |

The fraction (for the ring or bar) is the value over the target, from 0 to 1, and 0 when there is
nothing to count. A goal is **hit** when the fraction reaches 1, or when it was marked done; a dropped
goal is never hit. With habits (M4-03), check-ins of a habit linked to a numeric goal with the same
unit count toward it too.

## Copying last period's goals

When a new week (or month) has no goals yet, the owner can copy last period's: every goal that
wasn't dropped comes back open, with the same title, emoji, mode, target and unit, keeping its
parent only when the parent's period still overlaps the new one.

## Storage

`goals` and `goal_entries` are synced tables (Supabase migration 0009, replica migration 0004), and
`tasks.goal_id` links a task to the goal it serves. Deleting a goal takes it off its tasks.
