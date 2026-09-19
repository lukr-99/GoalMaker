# M2-12: Task details and the archive

**Status:** done 2026-09-19 · **Milestone:** M2

## Scope
- A task's detail view (spec, stories 12 to 19): title, notes with light Markdown, planned day and
  time, deadline, area, tags, checklist steps, repeat; edits sync like everything else.
- The archive of done tasks, newest first, searchable; reopening a task from it.

## Acceptance criteria
- Every field edits on both apps and round-trips through sync; the archive finds a done task by a
  word of its title.

## Result
- Every field of a task edits on both apps and writes through the outbox ([archive](../../docs/archive.md)):
  title, notes, day and time (a time needs a day), deadline, area, tags, repeat from presets, and
  the checklist of steps (add, rename, check, move, delete).
- Notes show as light Markdown, pinned by `contracts/vectors/markdown.json` on both apps: lines,
  list items, bold, italic and bare http(s) links you can open.
- The archive lists done tasks newest first and searches title and notes by every word, ignoring
  case and accents (`contracts/vectors/archive.json`); a task can be opened or reopened from it.
- Android: a tap on a task opens its details; the archive is in the lists' top bar. Windows: an open
  button on each row, an Archive page in the sidebar, and `--open archive`.

## Left
- Round-tripping through sync with a real backend couldn't be checked here (no Docker); the fields
  are ordinary replica writes, which the sync tests already cover.
