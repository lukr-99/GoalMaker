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
  in Today's top bar on Android. Opening Settings reads them, and the mark goes. An update waiting
  wears its own accent mark with a download arrow in the same place (ARCHITECTURE.md, updating); the
  two sit side by side.
- **They stay until they come right.** A problem that fixes itself, a sync that gets through on the
  next run, takes itself off the list with nobody doing anything. Something that fixes itself should
  never have interrupted anything in the first place.
- **A kind holds one problem at a time.** A newer one takes the place of the older, because what
  matters is what is wrong now, not every time it has been.
- **The same problem again is nothing new.** A sync that keeps failing on the same change reports
  the same thing on every run. That keeps the problem as it was, read or not, so the mark does not
  come back the moment Settings closes. Only a new kind, a new detail, or a problem that came right
  and then came back marks the item again.
- **Settings on screen is watching.** A problem that shows up while Settings is open is already
  read. A window hidden to the tray is nobody watching.

## What the owner sees

On Windows the marks say what they are, and Settings says what to do:

- **On the Settings item** a problem nobody has read is a warning triangle in the danger color, and
  an update is the accent circle with a download arrow. The tooltip and the screen reader name say
  which, for example "Sync hit a problem. Open Settings to see what to do.", or how many things wait
  when there is more than one.
- **At the top of Settings** a banner has one line for the update and one for each problem still
  wrong: what it is, what to do, a button (Install now, or a jump to the section that helps) and
  Dismiss. With nothing to act on there is no banner.
- **Dismiss** takes a line off the banner and its icon off the item until something new comes: a
  newer version, or a problem that came right and went wrong again. The problem itself stays in the
  Problems card until it comes right.
- `NoticeBoard` (Core) puts the update and the problems together for both places; `NoticeCopy` holds
  what each one says. The other sidebar items carry no marks.

## What belongs there

| Kind | Raised when |
|---|---|
| `sync` | the server refused a change, so it is still only on this device |
| `backup` | the weekly copy could not be written, or its folder has gone |
| `update` | an update could not be found, verified or installed |

The quiet daily check for updates never raises one: when it can't reach or trust the channel it
says nothing and tries again at its next look ([signing-and-releases.md](setup/signing-and-releases.md), step 4).

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
