# Repeating tasks

How a repeating task moves on (spec: repeat presets). The rules are pinned by
[`contracts/vectors/recurrence.json`](../contracts/vectors/recurrence.json) and run by both apps.

## Occurrences

A repeating task is a **series** of occurrences, each its own task row sharing a `series_id` (the
first occurrence's id). Only one occurrence of a series is open at a time: the next one appears
when the current one is **done** or **dropped** (skipped), whether from a list, Plan tomorrow or,
later, Claude. Moving an occurrence to another day moves only that occurrence; deleting it ends the
series (the earlier ones stay as history).

The next occurrence copies the current one's plan: title, notes, time, area, tags, top priority and
repeat. It starts open, without a deadline or steps.

## The next day

The rule is an RRULE subset ([composer](composer.md#repeats)): `FREQ` DAILY, WEEKLY or MONTHLY,
optionally `INTERVAL`, `BYDAY` (weekly) and `BYMONTHDAY` (monthly), in any order. Anything else
(another frequency, an unknown part, an interval below 1, a day of the month outside 1 to 31) is
not a rule the apps can follow: the task is treated as not repeating and no next occurrence
appears.

The next occurrence is the first day that matches the rule **after the later of the current
occurrence's day and today** (the planning day, [lists](lists.md)). Finishing early moves on from
the planned day; finishing late never creates an occurrence in the past.

Which days match counts from the current occurrence's day (the **anchor**), so moving an occurrence
moves the rest of the series with it:

| Rule | Matches |
|---|---|
| DAILY, interval n | every n-th day from the anchor |
| WEEKLY, interval n, BYDAY | those weekdays, in every n-th week (Monday to Sunday) from the anchor's week; without BYDAY, the anchor's weekday |
| MONTHLY, interval n, BYMONTHDAY d | day d of every n-th month from the anchor's month; months without day d are skipped; without BYMONTHDAY, the anchor's day |

An occurrence without a day uses today as its anchor.

## Two devices, one open occurrence

Occurrences are made on the device, offline too, so two devices can move the same series on. Two
rules keep a series down to one open occurrence once they sync:

1. **The next occurrence's id comes from the current one's**: a name-based UUID (version 5) of the
   current occurrence's id in GoalMaker's namespace (in the vectors), and its links to tags are named
   the same way from `<task id>/<tag id>`. Both devices finishing the same occurrence make the same
   rows, which sync merges into one.
2. **After every sync that pulled rows**, each device checks its series. If one has more than one
   open occurrence (for example, a device offline for two days pushed an occurrence the other had
   already finished), the one planned latest stays open (ties: the larger id) and the others are
   dropped. Every device reaches the same answer, so their writes agree. A finished occurrence
   reopened this way comes back as dropped rather than done: sync can't tell which it was.

**Reopening** an occurrence (undo, or changing a decision in Plan tomorrow) deletes its next
occurrence if that one is still open, so the series is back where it was.
