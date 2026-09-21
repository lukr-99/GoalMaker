# M6-08: Where problems show

**Status:** done · **Milestone:** M6

## Scope
- A sync problem currently sits across the top of Today: "Some changes didn't sync: HTTP 404:
  {"code":"PGRST205",...}". That is the wrong place and the wrong words. Today is for the day's
  plan, and a raw status code and JSON body say nothing an owner can act on.
- Problems move to one place in Settings, say what happened in plain words and what to do next, and
  keep the technical detail behind a "what happened" line for a bug report.
- The way in is quiet: a small mark on the Settings tab (Android) and on the Settings item in the
  sidebar (Windows) when something is waiting, cleared when it is read or once it comes right by
  itself. A sync that fixes itself on the next run should never have interrupted anything.
- What belongs there: changes that would not sync, an export or restore that failed, an update that
  could not be installed, the connector link's troubles. What does not: anything the owner just did
  and is watching, which keeps its own place (the restore report, the composer's refusals).
- The lists keep one quiet line for "not everything is on the server yet", without the error.

## Acceptance criteria
- View-model tests: a failed push puts one item in the Settings area and marks the tab; a later
  successful sync clears both without the owner doing anything.
- No string shown to an owner names an exception, a table, an HTTP code or a JSON body
  (docs/pitfalls.md, M6-06's string pass covers the rest).

## Done

2026-09-21. `contracts/vectors/problems.json` pins report, clear, read and the mark; `ProblemRules`
and `ProblemLog` run it in both apps. Sync reports what would not go and clears it on the next run
that works; on Windows the weekly backup reports a folder that has gone or a file that would not
write. Settings shows them newest first with what to do and "What happened" folded away, opening it
reads them, and the mark sits on the Settings item in the sidebar and on the gear in Today's top bar.
The lists keep one quiet line and no status code. docs/problems.md has the whole picture.
