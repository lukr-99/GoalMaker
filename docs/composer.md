# Composer grammar

One line in the composer becomes one item draft (spec: "Composer and shortcuts"). The parser is
pure: it takes the line, the local date and time, and the day rollover hour (04:00 by default), and
returns the draft plus the spans it recognized, so the composer can preview and highlight them.
Kotlin and C# implement it separately and both must pass
[`contracts/vectors/composer.json`](../contracts/vectors/composer.json).

## What a line can say

| You type | Meaning |
|---|---|
| `#tag` | A tag. Starts with a letter; letters, digits, `_` and `-` follow. Any number of tags. |
| `@Area` | The area. `_` stands for a space: `@Deep_work` is "Deep work". |
| `+Project` | The project, with the same naming as areas. A project nobody has made yet is made, the way `@Area` makes an area. |
| `!` | Top priority (a lone `!`; `Call mom!` is just punctuation). |
| `?` | An idea rather than a task (a lone `?`). |
| a date | `today`, `tomorrow` (`tmrw`, `tmr`), a weekday (`monday`, `mon`), `next monday`, `next week`, `next month`, `in 3 days`, `in a week`, `in 2 months`, `2026-10-05`, `21.9.`, `21.9.2027`, `sep 25`, `25 october`. `on` may come first: `on monday`. |
| a time | `17:00`, `9:30`, `5pm`, `5:30 pm`, `12am`, `noon`, `midnight`. `at` may come first. |
| a repeat | `daily`, `every day`, `weekdays`, `every weekend`, `weekly`, `monthly`, `every 3 days`, `every 2 weeks`, `every 2 months`, `every monday`, `every mon, wed and fri`, `every 15th`. |
| `/command` | At the very start: a command (`/plan`, `/review`, `/habit`, `/goal`); the rest of the line is its argument and nothing else is parsed. `/plan` opens [Plan tomorrow](plan-tomorrow.md) (inside the ritual it starts over); the others arrive with their milestones. |

Words are matched without regard to case. Punctuation stuck to the end of a marker or a date
(`#health,`, `tomorrow.`) goes with it. A backslash keeps a word as text: `\#launch`, `\tomorrow`.

## Where dates, times and repeats count

Tags, areas, projects, `!` and `?` count anywhere. Dates, times and repeats count only at the
**start** of the line or in its **tail**: the run of dates, times, repeats and markers that ends the
line. So `Prepare monday meeting` is just a title, while `Prepare monday meeting friday` plans the
task for Friday and keeps "monday" in the title. When one word could start several things, the
longest reading wins (`every monday` is a repeat, not a date).

When a line names more than one of something that can only be one (area, project, date, time,
repeat), **the last one wins**; all of them leave the title. Tags collect, each once, keeping the
first spelling.

## Dates

"Today" is the planning day: before the rollover hour (04:00), it is still yesterday, so
`tomorrow` typed at 01:30 means the day that has just begun.

- A weekday means the next one, never today: `friday` on a Friday is a week away.
- `next <weekday>` is that day in next week (weeks start on Monday); `next week` is next Monday,
  `next month` the first of next month.
- A date without a year that has already passed this year means next year.
- Impossible dates (`31.2.`) stay text.

A **time** without a date means today, or tomorrow when that time has already passed. A time never
moves an explicit date: `today 9:00` is today even at noon.

## Repeats

A repeat is saved as an RRULE subset (how a series moves on is in [repeating](repeating.md)): `FREQ=DAILY`, `FREQ=DAILY;INTERVAL=3`,
`FREQ=WEEKLY;BYDAY=MO,WE,FR`, `FREQ=WEEKLY;INTERVAL=2;BYDAY=FR`, `FREQ=MONTHLY;BYMONTHDAY=15`,
`FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=18`, parts always in that order and weekdays Monday first.

Without a date, the first occurrence is the first matching day from today (from tomorrow when a
given time has passed), and today counts: `every friday` on a Friday starts today. `weekly`,
`every 2 weeks`, `monthly` and `every 2 months` take their weekday or day of the month from the
planned date. A day some months lack (`every 31st`) starts in the first month that has it.

## The screen supplies the day

When a line names no day (and no repeat or time, which bring their own), the list you type on
supplies it: Today plans the task for today, Tomorrow for tomorrow, and the Inbox leaves it without
a day. The parser itself never guesses; the composer applies this after parsing.

## The result

The title is what remains, words joined by single spaces. It may be empty; the composer then has
nothing to save. The spans list every recognized part (kind, start, end in UTF-16 code units of
the line), in order, so the composer can highlight them and a chip can remove its own text.
