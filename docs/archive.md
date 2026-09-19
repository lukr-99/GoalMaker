# Task details and the archive

What a task holds beyond its line in a list (spec, stories 12 to 19), and how done tasks are kept and
found again. The archive's rules are pinned by
[`contracts/vectors/archive.json`](../contracts/vectors/archive.json) and run by both apps.

## Details

A task's detail view edits every field, and each edit is a normal write through the outbox, so it
syncs like everything else ([sync](sync.md)):

| Field | Rules |
|---|---|
| Title | 1 to 500 characters; a blank title is refused |
| Notes | up to 20,000 characters of light Markdown: `**bold**`, `*italic*`, lines starting with `- ` or `* ` as a list, and bare `http(s)` links, pinned by [`contracts/vectors/markdown.json`](../contracts/vectors/markdown.json); anything unmatched shows as typed |
| Planned day and time | a time needs a day, so clearing the day clears the time; setting them doesn't reopen a finished task |
| Deadline | a date of its own, independent of the planned day |
| Area | one area or none |
| Tags | any number, by name; a name used for the first time makes the tag |
| Steps | a checklist inside the task, each step 1 to 300 characters, checked, renamed, moved or deleted |
| Repeat | a rule the apps can follow ([repeating](repeating.md)) or none; a task that starts repeating becomes the first of its series |

## The archive

The archive lists **done** tasks, the most recently completed first; tasks completed at the same
moment go in id order. Open, dropped and deleted tasks stay out.

Searching splits the query on spaces. A task matches when **every** word is found somewhere in its
title or its notes, as part of a word or whole, ignoring case and accents: `mleko` finds
"Koupit mléko" and `Zavolát` finds "Zavolat do banky". An empty query shows every done task.

Reopening a task from the archive makes it open again on its planned day. A repeating task takes
back the next occurrence it made, if that one is still open ([repeating](repeating.md)).
