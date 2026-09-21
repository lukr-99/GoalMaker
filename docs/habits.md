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
(sick, travelling).

## Periods and streaks

Each period of a habit is in one state, the first that applies:

1. **none**: before the habit starts (`starts_on`), or a day the habit isn't due (a weekday outside
   its mask);
2. **met**: enough days in it are met (one for a day; N for a week or month);
3. **paused**: one of its days falls in a pause;
4. **skipped**: a check-in in it says skipped;
5. **open**: it hasn't ended yet (it holds today);
6. **missed**: otherwise.

A limit is read in a different order, because it is kept by default: **paused**, then **skipped**,
then **missed** as soon as a check-in goes over the number, then **open** while the day is still on,
and **met** once the day is over with nothing over the line.

The **streak** counts met periods back from the current one: an open, paused, skipped or none period
is passed over without counting or breaking it, and the first missed one ends the streak. So a "3
times a week" streak counts weeks, and a holiday pause or a sick day costs nothing.

**Pauses** are ranges of days (`from`, and `until` or open-ended while the pause lasts); they stay
after the habit resumes, so old streaks still read right.

## On screen

- **Today's ring** fills with the day's value against the target for a daily or weekday habit, and
  with the days met so far against N for a weekly or monthly one. A tap checks a check habit (and
  takes it back), adds one to a count, and asks for the value of an amount. The ring fills with a
  spring and a small burst, and a streak reaching 7, 14, 30, 50, 100, 200, 365, 500 or 1000 periods
  gets confetti; both are skipped under reduce motion.
- **The heatmap** gives each day a value: nothing before the start or on a day that isn't due,
  paused, skipped, over a limit, or the day's value against its target from 0 to 1 (a check is 0 or
  1; a limit's day is what is left of the allowance). It runs by
  weeks, Monday at the top, from the habit's first week and at most 26 weeks back, and shows the last
  weeks that fit the width.
- **Today** holds the habits due today as a row of rings above the rest of the tasks, with how many
  are left in the day's line (design spec, Today). Both apps also have a Habits screen with every
  habit, its streak and its map, and the archived ones folded at the end.
- A habit can be **archived**: it leaves Today and the list, keeping its history, and can come back.

## Goals

A habit can serve a goal. When the goal counts a number and the habit's unit is the goal's (ignoring
case and spaces), the habit's check-ins in the goal's period add to it like logged amounts (story 32,
[goals](goals.md)).

## Storage

`habits`, `habit_checkins` and `habit_pauses` are synced tables (Supabase migration 0010, replica
migration 0005). Deleting a habit takes its check-ins and pauses with it; deleting its goal leaves the
habit without one.
