# M3-02: The connector Edge Function

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- `supabase/functions/connector/`: a remote MCP server (official TypeScript SDK, Streamable HTTP,
  stateless, JSON responses) at `.../functions/v1/connector/<secret>` with JWT checks off for this
  function only (spec, Claude connector; ADR 0003).
- `supabase/functions/_shared/`: the owner-scoped database runner. Each call resolves the secret
  through `connector_resolve`, then runs its queries in one transaction as the `authenticated`
  role with the owner's claims and the `x-goalmaker-actor: claude` header, so row security and the
  activity log apply exactly as for the apps. No query touches user tables as the service role.
- Unknown or revoked link: 404 without detail; over the limit: 429.
- Deno pinned through npm next to the Supabase CLI; `deno task test` runs the unit tests, and an
  endpoint test drives the running function on the local stack (spec, Testing: connector).
- CI starts the edge runtime and runs both.

## Acceptance criteria
- `initialize`, `tools/list` and `prompts/list` answer over HTTP on the local stack.
- A revoked link gets 404 and the 121st call in a minute gets 429.
- A change made through the connector shows `claude` as the actor in the activity log.

## Result
- `supabase/functions/connector/index.ts` with the SDK's `WebStandardStreamableHTTPServerTransport`
  (stateless, JSON responses); `_shared/owner.ts` resolves the link and runs each call through
  `asOwner` (`set local role authenticated`, the owner's claims, `x-goalmaker-actor: claude`).
- Deno 2.1.4 pinned in `package.json` (the edge runtime's version); `supabase/functions/deno.json`
  has `test`, `check` and `lint` tasks. The SDK's `.js` subpaths need `@ts-types` hints
  (`_shared/deps.ts`), and a `prompts/get` without `arguments` gets `{}` before the SDK sees it.
- `connector/endpoint_test.ts` (10 steps) passes on the local stack, including 404 for unknown and
  revoked links and 429 over the limit; CI's Supabase job runs lint, types, unit and endpoint tests.
- docs/connector.md.
