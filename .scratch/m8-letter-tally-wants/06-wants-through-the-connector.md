# M8-06: Wants through the connector

**Status:** todo · **Milestone:** M8

## Scope
- Tools over `rules/wants.ts` (spec, story 108): `get_wants` (by state), `add_want` (the cooldown
  from the profile's thresholds unless given), `update_want`, `decide_want` (bought or dropped with a
  note, or reopened), `record_price_check` (a price, where it was found, alternatives as a note).
- Descriptions that tell Claude to look prices up with its own web search and to confirm a decision
  with the owner first. GoalMaker never fetches from a shop.
- `docs/connector.md` gains the tools in its table and a sample prompt ("Which of my wants are
  ready? Check the prices and ask me about each.").

## Acceptance criteria
- Endpoint tests on the local stack: add with and without a price, the cooldown matches the apps',
  a price check recorded, decided, undone; a want Claude adds shows "by Claude" in both apps.
- `deno task test` runs `wants.json`.

## Vectors to add
- None beyond M8-03's; the TypeScript rules run `wants.json`.

## Check
- Emulator and Windows: a want added and decided by Claude appears and settles on both.
- Endpoint: the new tools through MCP against `npx supabase functions serve`.
