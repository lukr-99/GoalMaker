# The Letter

> M8 (`.scratch/m8-letter-tally-wants/`): `get_review_digest` (M8-07), the Letter in both apps
> (M8-08) and the routine's prompt (M8-09) are built. Setting the routine up is the owner's one step
> below.

The Letter is a text about a finished week (or month) that a scheduled Claude routine writes and
GoalMaker keeps as that review's summary (spec, stories 100 to 103; [ADR 0012](adr/0012-the-letter-written-by-a-routine.md)).
It is meant to be read before the review, not instead of it: the owner reads the letter, then looks
back, reflects and rates the week as always ([reviews](reviews.md)).

## Who does what

| Part | Lives in | Does |
|---|---|---|
| The routine | Claude (a scheduled routine on the owner's plan) | Gathers, writes, saves, and may email |
| `get_review_digest` | The connector | One period of GoalMaker in one answer |
| Other apps' connectors | Those apps | Their own data, straight to the routine |
| `save_review_summary` | The connector | Stores the letter as the review's `summary` |
| The Letter step | Both apps | Shows it first in the guided review |

No other app's data passes through GoalMaker's backend, and GoalMaker sends no email.

## `get_review_digest`

`get_review_digest(kind = weekly | monthly, period?)` returns, as JSON (`period` is any day in the
week or month):

- `period`: kind, start and end (the start is what `save_review_summary` must be given back);
- `done`: tasks finished, by the day they were done, with area and project;
- `open`: `left` (still open and planned in the period, with move counts), `overdue` (every open
  task planned before today) and `slipping` (left ones moved three times or more);
- `goals`: the period's goals with progress against where they should be by now, in percent;
- `habits`: each habit's periods met, missed and skipped, and its streak;
- `projects`: items moved to Done in the period, per project;
- `triggers`: the reactive prompt triggers that fired, with their subjects ([reviews](reviews.md));
- `review`: this period's mood, energy and reflections if the review was already done;
  `last_letter`: the last period's letter;
- `next`: the next period's planned tasks, deadlines and goals set so far;
- `wants`: wants that became ready or were decided in the period, and those ready next period
  ([wants](wants.md));
- `tally`: minutes per category and per project, per device kind ([tally](tally.md)), once Tally
  exists (M8-10 to M8-14).

The prompts `weekly_review` and `monthly_review` are built from the same digest (`reviewDigest` in
`supabase/functions/_shared/planner`, over `rules/digest.ts`), so the two never disagree. Their
text is pinned by snapshots over a fixed August in the connector's endpoint test.

**Which period by default:** the week or month that holds **yesterday's** planning day. Run on Sunday
evening, that is the week ending now; run on Monday morning, the week just gone. The rule and the
digest's shape are pinned by the `digest` group of `contracts/vectors/reviews.json`.

## In the apps

- **The Letter step** comes before the look-back when the review has a summary: the period's dates as
  the headline in the accent color, a "by Claude" mark, the letter in light Markdown (with `#` to
  `###` headings, [archive](archive.md)) at a reading width (about 680 px on Windows; on the phone the
  top bar folds away while it is read), and a filled Continue (Enter on Windows). Without a letter
  the review starts at the look-back as before, and back from the look-back returns to the letter.
- **The Reviews screen** marks a review with a letter by an accent envelope and shows its first line
  of text (the first heading only when there is nothing else); opening it lands on the Letter step.
- **The review reminder** says "Your letter for the week is here" when the letter arrived before it
  rings, and opens the Letter step (`letterWaiting` in `contracts/vectors/reminders.json`).
- The apps never edit a letter. Asking Claude, or running the routine again, replaces it.

## The routine

The owner's one-time step: in Claude, create a scheduled routine for **Sunday 18:00** (the owner's
time zone) with GoalMaker turned on, plus any of the other connectors, and this prompt. A monthly
routine is the same prompt with `monthly` on the 1st at 07:00.

> Use GoalMaker. Call get_review_digest with kind weekly and no period. If these connectors are on,
> also read them for the same dates: the Me app (journal and people, upcoming birthdays), Drawer
> (deadlines and what expires soon), Learning (progress). Skip any that are off without saying so.
>
> Write me a letter about the week, in plain words, as a friend who has read all of it. Sections, as
> short Markdown headings: what went well, what slipped, patterns I might have missed, what is
> coming up (birthdays, deadlines, wants that become ready), and one focus for next week. Be specific
> and use the numbers. At most 350 words. No lists longer than five lines. Leave out a section
> the week gives nothing for. When the week was quiet, say so in a few sentences and don't pad it.
> Never invent anything the data doesn't show.
>
> Save it with save_review_summary, kind weekly, and period set to the digest's period start. That
> is the only change to make: don't change anything else in GoalMaker or in any other app.

Optional last line, when a Gmail connector is on: "Then email me the letter with the subject
'Letter for the week of <period start>'." GoalMaker itself sends nothing.

**What a run does**, checked on the local stack: one `get_review_digest` and one
`save_review_summary`. The activity log shows a single change by Claude, a new review for the week
or an update of its summary; running again replaces the letter in the same review. A week with
nothing in GoalMaker still gets a short, honest letter rather than none, so the review opens on it.

**To set it up:** in Claude, open Routines (or ask Claude to schedule a task), pick Sunday 18:00 in
your time zone, turn on GoalMaker and whichever other connectors the prompt names, and paste the
prompt. Run it once by hand and open the review on the phone or the PC to see the letter.
