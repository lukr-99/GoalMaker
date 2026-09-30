# M8-08: The Letter in both apps

**Status:** done · **Milestone:** M8

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

## Result

2026-09-28, both apps.

- **Light Markdown** gains headings: a line starting with one to three `#` and a space, on both apps,
  pinned by six new cases in `markdown.json` (levels, four hashes, `#hashtag`, styles inside, and a
  letter with headings, a list and a link). Task notes get them too.
- **`ReviewRules.letterWaiting`** on both apps, pinned by the new `letterWaiting` group of
  `reminders.json` (9 cases): the review of the period the reminder looks back on has a summary that
  isn't blank and isn't deleted. The reminder reads the reviews when it rings, so "saved after it"
  is simply a reminder that already said the usual thing.
- **The Letter step** comes first when the review has a letter, on both apps: the dates in the
  accent, "by Claude", the letter in light Markdown, and a filled Continue. Android folds the top bar
  away while it is read; Windows keeps it at 680 px and Enter continues. Back from the look-back
  returns to the letter; without a letter the review starts at the look-back and the progress bar
  counts one step fewer. The apps never write the summary.
- **Reviews:** an accent envelope and the letter's first line of text (`ReviewRules.letterPreview`,
  the first heading only when there is nothing else) on every past review with a letter.
- **The review reminder** says "Your letter for the week (or month) is here." when the letter came
  first. Tapping it opens the review, which starts on the letter.
- **Checked:** view-model tests on both apps (starts on the letter or on the look-back, back returns
  to the letter, the Reviews list marks and previews it; on Windows also the reminder's text), the
  Markdown and reminder vectors on both apps, 483 Windows tests, `dotnet format`, the accessibility
  scan, Android unit tests, build and lint, and repository validation.
- **Left:** the emulator and Windows checks: a summary saved through the connector, the reminder
  mentioning it, a long letter scrolling, the four themes and the largest text size, and the reading
  width in a wide and a narrow window.
- **Checked (2026-09-29):** on the emulator and the Windows dev app against the local stack, a letter
  saved through the connector opened the review on both, scrolled with the top bar folding away on
  the phone, took Enter and Back on Windows and wrapped in a narrow window; the reminder rang with
  the letter's words and opened it.
