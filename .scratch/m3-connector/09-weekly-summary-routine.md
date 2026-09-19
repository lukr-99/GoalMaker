# M3-09: Weekly summary routine

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- A `reviews` table in the v1 shape (kind, period, mood, energy, reflections, summary) and a tool
  that saves a review summary for a week or a month (spec, story 75).
- docs/connector.md: a routine prompt for a scheduled Claude routine (or another MCP-capable
  assistant) that reads the week through the connector and saves the summary.
- The apps show review summaries with the review screens in M4; until then the saved summary is in
  the activity log.

## Acceptance criteria
- pgTAP row security for `reviews`; an endpoint test saves a summary and reads it back.

## Result
- Migration 0008 (`reviews`, a synced table: kind, period start, mood, energy, summary; one row per
  kind and period, checked to start on the period's first day) and replica migration 0003. The
  reflections column waits for M4, which needs a JSON column kind in the synced-tables contract.
- `contracts/vectors/reviews.json`: a review's id (UUID version 5 of `review/<owner>/<kind>/<start>`)
  and period start, run by Kotlin, C# and the connector, so every writer makes the same row.
- `save_review_summary` and `get_review_summaries`; the review prompts end with saving the summary.
- docs/connector.md, "A weekly summary routine".
