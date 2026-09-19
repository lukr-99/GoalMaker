# Habits

Habits are the things the owner wants to keep doing (spec, stories 36 to 42). The rules below are
pinned by [`contracts/vectors/habits.json`](../contracts/vectors/habits.json) and run by both apps
and the connector.

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

The **streak** counts met periods back from the current one: an open, paused, skipped or none period
is passed over without counting or breaking it, and the first missed one ends the streak. So a "3
times a week" streak counts weeks, and a holiday pause or a sick day costs nothing.

**Pauses** are ranges of days (`from`, and `until` or open-ended while the pause lasts); they stay
after the habit resumes, so old streaks still read right.

## On screen

- **Today's ring** fills with the day's value against the target for a daily or weekday habit, and
  with the days met so far against N for a weekly or monthly one.
- **The heatmap** gives each day a value: nothing before the start or on a day that isn't due,
  paused, skipped, or the day's value against its target from 0 to 1 (a check is 0 or 1).

## Goals

A habit can serve a goal. When the goal counts a number and the habit's unit is the goal's (ignoring
case and spaces), the habit's check-ins in the goal's period add to it like logged amounts (story 32,
[goals](goals.md)).

## Storage

`habits`, `habit_checkins` and `habit_pauses` are synced tables (Supabase migration 0010, replica
migration 0005). Deleting a habit takes its check-ins and pauses with it; deleting its goal leaves the
habit without one.
