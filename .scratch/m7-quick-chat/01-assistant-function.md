# M7-01: The assistant function

**Status:** todo · **Milestone:** M7

## Scope
- A new Edge Function, `assistant`, called by the apps with the owner's own session (the gateway's
  JWT check on, unlike the connector). It runs each call as that owner through `asOwner`, so row
  security applies exactly as for the apps (spec, "Quick chat (M7)").
- A `ChatProvider` interface in `_shared/assistant/`: given the conversation and the tools, return
  text or tool calls. `GeminiProvider` implements it over the Gemini API's function calling, with the
  key from the `GEMINI_API_KEY` secret; a `FakeProvider` plays scripted turns in tests. Another
  provider (a local Ollama model on the PC) can come later without touching the loop.
- The tool loop: the same `tools` list the connector serves, minus the ones that delete (open
  decision), turned into function declarations from their zod shapes. At most 8 tool rounds a
  request; the answer is plain text.
- A system prompt with the owner's planning day, time zone and a short note on how GoalMaker words
  things, kept in `_shared/assistant/prompt.ts`.
- Limits: 30 requests a minute and a daily cap per owner (a small table or the connector's rate
  limit pattern), and a clear message when Gemini's own free-tier limit is hit.
- Who a change is by (open decision): proposed, the owner, with the activity log noting the chat.

## Acceptance criteria
- Endpoint tests on the local stack with the fake provider: a request that adds a task, one that
  reads Today, one that needs two tool rounds, a delete refused, the round limit, the rate limit, and
  another owner's rows never reachable.
- A unit test that every tool's input schema becomes a valid Gemini function declaration.
- No Gemini call in CI.

## Check
- Endpoint: with a real key on the local stack, "add call the bank tomorrow at 9" and "what's on
  today?".
