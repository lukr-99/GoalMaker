# M8-06: Wants through the connector

**Status:** done · **Milestone:** M8

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

## Result

2026-09-28, the connector and both apps.

- `WantList` (`_shared/planner/wantList.ts`) reads and changes wants as the owner, and
  `_shared/tools/wantTools.ts` serves `get_wants` (ready, cooling, decided, open or all),
  `add_want`, `update_want`, `decide_want` (bought, dropped or reopen) and `record_price_check`.
- A new want takes its cooldown from the owner's `want_cooldowns` row, or the defaults without one,
  through `rules/wants.ts`, the rules that run `wants.json`. A picked number of days wins. The
  currency defaults to the one the thresholds use.
- A price check keeps the price in the want's currency, and "where" plus the alternatives as the
  checked note, which both apps already showed.
- **Both apps:** a want Claude added says "by Claude" after its price and state. Neither app showed
  who made a want before.
- **Checked:** `deno task test` runs `wants.json`. The endpoint test on the local stack passes:
  cooldowns from the defaults (500, exactly 1,000, no price, another currency), from the owner's own
  thresholds and from a pick; `made_by` and the activity log say Claude; an edit keeps the cooldown;
  a price check with alternatives; bought with a note, undone through `undo_change`, dropped,
  reopened, and a second reopen and a stranger's id refused. Windows and Android build and pass their
  tests, with a Windows test for "by Claude".
- **Left:** the emulator and Windows check that a want Claude adds and decides appears and settles
  on both.
- **Checked (2026-09-28):** on the emulator and the Windows dev app against the local stack, a want
  Claude added and decided showed by Claude with its price check and note on both. On the cloud
  (2026-09-30), `get_wants` answers through the reconnected connector.
