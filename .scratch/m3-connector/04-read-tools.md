# M3-04: Tools that read

**Status:** todo · **Milestone:** M3

## Scope
- Read Today (with its sections), Tomorrow, the Inbox, one task with its steps, tags, reminders
  and notes, areas and tags, and search including the archive (spec, story 70).
- Answers are short structured text Claude can quote, with ids for follow-up calls; dates in the
  owner's planning day.
- Goals, habits and projects arrive with their tables in M4 and M5, and get their tools there.

## Acceptance criteria
- Endpoint tests on the local stack: a seeded owner's Today through the connector matches the list
  rules' sections, a stranger's rows never appear, and search finds a done task in the archive.
