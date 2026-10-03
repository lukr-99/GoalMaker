# Reviews

A review is a look back at a week, a month or a year, and a look forward (spec, stories 59 to 66).
One review exists per kind and period: a week from its Monday, a month from its first day, a year
from January 1. Every device and the connector name it the same way, a UUID version 5 of
`review/<owner>/<kind>/<period start>`, so a review written on the phone and one written on the PC
are the same row ([`contracts/vectors/reviews.json`](../contracts/vectors/reviews.json)).

A review holds the owner's mood and energy (1 to 5), the summary a Claude routine may write
([connector](connector.md)), and the **reflections**: the prompts it asked and the answers written,
in the order they were asked. The summary is the [Letter](letter.md): when there is one, the guided
review starts on it, and the apps only show it, never edit it.

## The prompt library

[`contracts/content/prompts.json`](../contracts/content/prompts.json) is the library both apps and
the connector ship: a hundred prompts in ten categories (wins, lessons, energy, focus, gratitude,
people, health, obstacles, craft, next), plus the triggered ones below. A prompt has

- an **id** like `wins/proud`, which is what a reflection stores;
- the **reviews** it suits, so a weekly review never asks a question meant for a year;
- its **text**, where `{period}` becomes week, month or year and `{subject}` the thing it is about.

`tools/check_prompts.py` keeps the file sound: ids, categories, kinds, placeholders, one prompt per
trigger, and enough prompts in every category to rotate.

## Which prompts a review asks

**The rotation** walks the categories in the file's order, starting at the one after the last prompt
the owner saw, and takes from each the prompt of that category shown longest ago (anything unseen
comes first). A category that runs out simply starts again with its oldest, so nothing repeats until
the rest of its category has been asked.

**Reactive prompts** come from the period's own facts (story 63), worst first, before the rotation:

| Trigger | When |
|---|---|
| `goal_behind` | a goal is 0.2 or more behind where its period says it should be |
| `habit_missed` | a habit missed at least half of its periods (with two or more of them) |
| `task_slipping` | a task was moved three times or more |
| `habit_streak` | a habit is on a streak of 7 periods or more |
| `goal_ahead` | a goal is 0.2 or more ahead of plan |
| `no_goals` | the period had no goals |
| `quiet_period` | the tasks done are at most half the usual (not in a yearly review) |
| `busy_period` | the tasks done are at least one and a half times the usual (not in a yearly review) |

Ties are broken by name, so two devices ask the same thing.

## Starting a review

The Reviews place on both apps offers last week, this week, last month, this month, last year and
this year, each marked once something is written in it. A yearly review is the same guided flow as
the others over the whole year: the look back counts the year, the goals step sets next year's
goals, and the prompts are the ones that suit a year. It has no reminder, so finishing it records no
ritual run.

## The January nudge

A new year should start with intent (spec, story 66). Through January, Today opens with a card while
there is still something to do for the year: **Look back on 2026** when last year has no written
yearly review, and **Set 2027 goals** when this year has no year goals yet (dropped and deleted ones
don't count). Each button shows only while its part is left, and the card goes once both are done.
**Not now** puts it away for the rest of the year on that device; next January it comes back. The
rule is the `newYear` group of
[`contracts/vectors/reviews.json`](../contracts/vectors/reviews.json), run by both apps and the
connector, whose `get_today` adds the same line in January (it never sees Not now, which each device
keeps to itself).

## Storage

`reviews` is a synced table (Supabase migration 0008, replica migration 0004). Migration 0011 added
`reflections`, a JSON array of `{"prompt": "<id>", "answer": "<text>"}` with at most twenty entries;
a trigger keeps every entry in that shape. JSON is a column kind of its own in
[`contracts/schemas/synced-tables.json`](../contracts/schemas/synced-tables.json): the replica keeps
the JSON text, the wire carries the value.
