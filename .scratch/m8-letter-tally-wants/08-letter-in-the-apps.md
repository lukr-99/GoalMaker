# M8-08: The Letter in both apps

**Status:** todo · **Milestone:** M8

## Scope
- Android, then Windows (spec, stories 101 and 102; [letter](../../docs/letter.md)):
  - **The Letter step** before the look-back when the review has a summary: the period's dates as
    the headline in the accent, a "by Claude" mark, the letter in light Markdown at a reading width
    (about 680 px on Windows; the page padding on the phone, with the top bar collapsing), and a
    filled Continue. The step slides in like the others; no confetti.
  - **The Reviews screen:** an accent envelope and the letter's first line on a review that has one;
    opening it lands on the Letter step.
  - **The review reminder** says the letter is here when it arrived first, and opens the Letter step.
- The apps never edit the summary; `ReviewList.setSummary` stays unused.
- Windows already loads `SummaryText` without showing it; Android already has `ReviewItem.summary`.

## Acceptance criteria
- View-model tests on both apps: with a letter the review starts on the Letter step, without one on
  the look-back; the Reviews list marks and previews it; the reminder's text and target.
- `LightMarkdown` renders a letter with headings, lists and links.

## Vectors to add
- `contracts/vectors/reminders.json`: a `letterWaiting` group (a letter saved before the reminder,
  after it, for another week, deleted).
- `contracts/vectors/markdown.json`: a letter-shaped case, if a letter needs more than notes use.

## Check
- Emulator: save a summary through the connector, the reminder mentions it, the review opens on it,
  a long letter scrolls, all four themes, the largest text size.
- Windows: the same, the reading width in a wide and a narrow window, the keyboard to Continue.
- Endpoint: `save_review_summary`, then both apps after a sync.
