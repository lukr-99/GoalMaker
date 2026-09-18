# M2-07: The Plan tomorrow ritual

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- A guided flow: each unfinished task of today gets a decision (tomorrow, a date, drop), then
  pick tomorrow's top priorities; started from Today, `/plan` or the evening reminder.

## Acceptance criteria
- After the ritual nothing from today is left undecided, and tomorrow has its priorities.

## Result
- Rules in `docs/plan-tomorrow.md`, pinned by `contracts/vectors/plan.json` (7 cases: what step 1
  reviews and in what order, what each task shows, tomorrow's order and priority count), passed by
  both apps.
- Step 1 asks about every open task planned today or earlier: Tomorrow, a Date (calendar, days after
  today), Done or Drop (Done added to the spec's three). Decisions save at once, can be changed, and
  are read back from each task's state, so edits from the other device show. Next waits until
  nothing is undecided.
- Step 2 lists tomorrow by time (rows don't jump when flagged), flags up to 3 top priorities, has
  the composer (lines land on tomorrow) and the Inbox with a one-tap Tomorrow. Then a summary card.
- Opened from the Plan tomorrow button on the lists, `/plan` in the composer, and on Windows
  `--open plan` / `goalmaker://open/plan`. New task operations: plan (reopens), drop, top priority.
- Verified: on the emulator by hand through every step (decisions, the date picker, a priority,
  an Inbox task, a composer line, the summary), and the phone's results showed on the PC's Tomorrow.
  On Windows by headless view-model tests (8) and offscreen renders of all three steps
  (`PageSnapshots`, an explicit test that draws pages to PNG without a window).
- Open for later: the evening reminder and "already planned today" (M2-09); Done and Drop on
  repeating tasks moving the series on (M2-08).
