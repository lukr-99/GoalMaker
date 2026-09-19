# M3-06: Ritual prompts

**Status:** todo · **Milestone:** M3

## Scope
- MCP prompts for Plan tomorrow, Weekly review and Monthly review (spec, story 74), each carrying
  the owner's real data for the period and the steps of the ritual as the apps run it
  (docs/plan-tomorrow.md).
- A tool that records the Plan tomorrow ritual as done for the planning day, so the evening
  reminder stays quiet on both devices (docs/reminders.md).

## Acceptance criteria
- `prompts/get` returns each prompt with the owner's tasks for the period; recording the ritual
  writes the same `ritual_runs` row the apps would.
