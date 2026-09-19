# M3-09: Weekly summary routine

**Status:** todo · **Milestone:** M3

## Scope
- A `reviews` table in the v1 shape (kind, period, mood, energy, reflections, summary) and a tool
  that saves a review summary for a week or a month (spec, story 75).
- docs/connector.md: a routine prompt for a scheduled Claude routine (or another MCP-capable
  assistant) that reads the week through the connector and saves the summary.
- The apps show review summaries with the review screens in M4; until then the saved summary is in
  the activity log.

## Acceptance criteria
- pgTAP row security for `reviews`; an endpoint test saves a summary and reads it back.
