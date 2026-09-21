# Where a problem shows

Some things go wrong while nobody is watching: a change that would not reach the server, the weekly
copy of your data that could not be written, an update that would not install. They all end up in one
place, and that place is not the top of Today.

The rules are pinned by [`contracts/vectors/problems.json`](../contracts/vectors/problems.json) and
run by both apps.

## One place, one quiet mark

- **Settings** holds them, newest first, each one saying what happened in plain words, what to do
  about it, and when. The technical line is folded away behind **What happened**, for a bug report.
- **The way in is quiet**: a small mark on the Settings item in the Windows sidebar, and on the gear
  in Today's top bar on Android. Opening Settings reads them, and the mark goes.
- **They stay until they come right.** A problem that fixes itself, a sync that gets through on the
  next run, takes itself off the list with nobody doing anything. Something that fixes itself should
  never have interrupted anything in the first place.
- **A kind holds one problem at a time.** A newer one takes the place of the older, because what
  matters is what is wrong now, not every time it has been.

## What belongs there

| Kind | Raised when |
|---|---|
| `sync` | the server refused a change, so it is still only on this device |
| `backup` | the weekly copy could not be written, or its folder has gone |
| `update` | an update could not be found, verified or installed |

What does **not** belong there is anything the owner just did and is watching: an export they asked
for, a restore's report, a line the composer would not take. Those answer where they were asked.

## What the lists say instead

A list keeps one quiet line, "Not everything is on the server yet", and nothing else. It does not
carry the status code or the server's words, which say nothing anyone can act on and belong with the
rest of the detail in Settings.

## Where it lives in the code

`ProblemRules` is pure: report, clear, read, and whether anything is unread. `ProblemLog` holds the
list for as long as the app runs, and nothing is written to disk: a problem that has not come right
by the next start reports itself again. The composition root is what knows when to report, so sync,
the weekly backup and the updater stay unaware there is a Settings page at all.
