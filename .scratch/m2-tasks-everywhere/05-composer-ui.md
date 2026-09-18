# M2-05: The composer with a live preview

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- The floating pill on both apps that grows a preview of parsed chips as you type; Enter or Send
  saves; Esc clears; tapping a chip edits or removes it; `/` shows commands.
- Unknown `@Area` or `#tag` offers to create it.

## Acceptance criteria
- End to end on both apps: a line with every shortcut saves the item exactly as previewed and syncs.

## Result
- Both apps: the composer grows chips for date, time, repeat (in words), area (with its color, or
  "new"), tags ("new" when unknown), top priority, and "later" chips for projects and ideas until
  M5; commands show as "coming soon". Tapping a chip removes its part of the line.
- Saving writes the task with day, time, priority and repeat, creates a new area (next unused
  palette color) or tags, and links the tags, in one transaction.
- Verified end to end: a line typed on the phone and one typed on the PC saved exactly as
  previewed, reached the server, and appeared on the other device with their areas.
- Open: an explicit "create area?" confirmation, if the "new" note proves too quiet.
