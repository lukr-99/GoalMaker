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

## Sharing into GoalMaker

On Android, anything another app can share as text opens the same composer (spec, story 10): the
shared text becomes the line, so every shortcut above works on it, and the link it came from waits
in the notes, so the item carries where it came from. A share with only a link takes the link's host
and path as its title; a shared passage keeps its first line as the title and the rest in the notes.
The sheet offers the owner's areas and projects as chips, which write their own `@Area` and
`+Project` into the line. With no day, area or project chosen, the item lands in the Inbox.

## The result

The title is what remains, words joined by single spaces. It may be empty; the composer then has
nothing to save. The spans list every recognized part (kind, start, end in UTF-16 code units of
the line), in order, so the composer can highlight them and a chip can remove its own text.

## The bottom bar on every list

The composer is the bottom bar of Today, Tomorrow and the Inbox, and of Wants, Habits and Goals
(the owner's pick from the add prototype, v2, 2026-10-01). There is no add button in their top bars
or headers any more. On Goals the "Add a goal" rows under each period and the List view's per-group
add button stay as second ways in.

The round button at the bar's end does two jobs:

- **While the line is empty** it shows a plus and opens the page's full form: the new task form on
  Today, Tomorrow and the Inbox (its day starts as the list's), the want form on Wants, the habit
  form on Habits and the goal form on Goals. Its name says so ("New task", "New want", ...).
- **Once something is typed** it turns into the send arrow and adds what the line says, with a
  live preview of it above the line. Its name becomes "Add task", "Add want", ... The plus turns
  into the arrow with a quick spin and fade (the theme's quick and standard durations); with reduce
  motion on, the icons only cross-fade.

A line that can't be added as it stands opens the form instead, filled in with what was read: a
want without a reason (the reason is required), or a habit or goal with no name left. In the quick
chat the button is always the send arrow, and the line goes to the chat on every page.

On the phone the plus opens a bottom sheet: the new task sheet (title, when it is planned, area,
top priority and notes, starting on the tab's day), or the want sheet and the habit and goal forms
that were there before. The button's shape goes from a rounded square to a circle as it turns into
the arrow. The bar's chips on Wants, Habits and Goals only show what will be saved; they have no
remove button. Opened on top of Today rather than as a tab, Habits and Goals keep their own quick
chat thread while they are open.

On Windows Enter adds (or sends to the chat), Esc clears (on a line that is already empty it is left
to the window, so a second Esc closes a mini window), and Ctrl+N opens the full form, filled
in with what the line says so far. Tab takes the chips' remove buttons, the chat switch, the line and
the round button in that order, as they show. The plus opens the new task form over the list, the want panel at
the top of Wants, or the habit or goal editor over the page. The quick-add box and Plan tomorrow keep their send-only bar.

## Adding on Wants, Habits and Goals

The bar on Wants, Habits and Goals reads a line with small, predictable rules of its own, pinned by
[`contracts/vectors/quick-add.json`](../contracts/vectors/quick-add.json) and run by both apps and the
connector's rules. The connector's `add_want`, `add_habit` and `add_goal` read a `line` with them too,
which is how the quick chat adds from a short line ([assistant](assistant.md#speed)). A line is read word by word, a word being what sits between spaces, compared
without case and without the punctuation at its end. The first phrase of each kind counts; a later
one, and anything not understood, stays in the title. A number is written with one or two decimals
after a dot or comma (`9,50`, `2.5`), or with groups of three after a dot, comma or space
(`12 990`, `10,000`).

**A want** (`Kindle 3 290 Kč wait 2 weeks because I read on the train`):

| Part | Reads |
|---|---|
| reason | everything after the first `because` |
| price | a number with a currency after it (`Kč`, `kc`, `CZK`, `€`, `EUR`, `$`, `USD`, `£`, `GBP`, apart or joined: `3290Kč`), or a sign before it (`€12`, `$19.99`). A bare number stays in the title (`iPhone 15`). |
| wait | `wait` (`for`) N `days`, `weeks` or `months`, or `a week`; a month is 30 days, and more than 365 days stays text. Without it the price picks the cooldown, as in the form. |

The preview shows the price, the wait (picked or from the price) and the reason, or a warning that
the reason is missing.

**A habit** (`Swim 2 times a week 40 min`):

| Part | Reads |
|---|---|
| how often | `daily`, `every day`; `N times a week` (`3x a week`, `3 x per week`, `once`, `twice`), 1 to 7, and `N times a month`, 1 to 31; `weekly` and `monthly` (once); `weekdays`, `weekends`; `every` or `on` with day names (`every mon, wed and fri`, `on tue thu`). Nothing said is every day. |
| how much | `N times` (`twice`) `a day` is a count; N and a unit (`8 glasses`, `30 min`, `5km`), maybe with `a day`, is an amount when the unit is time, distance, steps, pages, volume, energy or weight, or N has decimals, and a count otherwise. A small word is never a unit (`2 of my friends`). Nothing said is a plain check. |

Both leave the name. A limit (at most) is set in the form.

**A goal** (`Read 3 books this month`):

| Part | Reads |
|---|---|
| period | `this` or `next` `week`, `month` or `year`; `today`, `tomorrow`; `in` a month (`in November`, `in oct`: this year, or next year when it has passed) or a year (`in 2027`, not one gone by). Nothing said is this week. |
| target | the first number with a unit (`30 km`, `3 books`, `3km`) makes it a number goal with that target. It stays in the title, so "Read 3 books" reads as it was typed. Without one the goal is done or not. |
