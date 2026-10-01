# The quick chat

The composer can switch from quick-add to a chat: the owner types a request in plain words ("move
everything from today to Friday", "what's left for the GoalMaker project?") and a model answers and
acts through the same tools the [Claude connector](connector.md) serves (spec, "Quick chat (M7)";
`.scratch/m7-quick-chat/`). This page is about the server side, the `assistant` Edge Function.

## The call

`POST /functions/v1/assistant` with the owner's own session (`Authorization: Bearer <access token>`)
and the whole thread so far, the last message the owner's:

```json
{"messages": [{"role": "user", "text": "what's on today?"}]}
```

The answer is `200 {"text": "..."}`. A failure is `{"error": code, "message": "..."}`:

| Code | Status | When |
|---|---|---|
| `unavailable` | 503 | The server has no `GEMINI_API_KEY` |
| `rate_limited` | 429 | Our limits: 30 requests a minute or 200 a day for this owner |
| `provider_limit` | 429 | Gemini's free tier is used up (its 429 or `RESOURCE_EXHAUSTED`) |
| `bad_request` | 400 | Not the shape above: no messages, more than 60, a text over 4,000 characters, a role other than `user` or `model`, or the last one not the owner's |
| `failed` | 502 | Gemini or the function failed in any other way |

A call without a valid session never reaches the function: the gateway answers 401 itself.
The `message` is plain English the apps may show as it is.

## What it does

1. The gateway checks the session's JWT (`verify_jwt = true` in `supabase/config.toml`, unlike the
   connector). The function takes the owner from the token's `sub`.
2. It counts the request against the limits (below).
3. It builds the system prompt (`_shared/assistant/prompt.ts`): the owner's name, their local time,
   time zone and planning day (which starts at their day-start hour, not midnight), and a short note
   on how GoalMaker words things, from [CONTEXT.md](../CONTEXT.md).
4. It runs the tool loop (`_shared/assistant/toolLoop.ts`): the model answers in words or asks for
   tools; each tool runs, its result goes back, and so on. At most 8 rounds of tool calls and 10
   calls a round; after the eighth round the model must answer in words, and if it still asks for
   tools the chat says it stopped and that what it changed so far stays.
5. Every tool call runs in its own transaction through `asOwner`: the `authenticated` role with the
   owner's claims, so row security applies exactly as for the apps. A tool's arguments are checked
   against its zod shape first; a wrong one goes back to the model as an error.

Nothing of the conversation is kept on the server. The apps hold the thread and send it whole each
time; it is gone when the app closes.

## What it cannot do

- **Delete.** The chat is offered only its everyday tools (see "Speed"), and never one that
  deletes, even if it were listed: all the `delete_*` tools, and every tool marked destructive or
  named `remove_*` (`remove_step`, `remove_reminder`, `undo_change`, which can delete what it undoes). A call for a tool it wasn't
  offered is refused before anything runs. Asked to delete, it says the owner can do it in the app.
- **Say who made a task.** `made_by` isn't offered; a task added in the chat is the owner's.
- Reach another owner's rows, or anything the owner's own session can't reach.
- Read the web or anything outside GoalMaker.

## Who a change is by

The owner: they asked for it in their own app. The function sends the actor header as `owner` and
a second header, `x-goalmaker-via: chat`. The activity log trigger (migration 0019) writes that into
`activity_log.via`, which is `chat` for a change made through the chat and null for everything
else. So the log keeps the owner as the actor (no new `made_by` or actor value) and still says the
change came through the chat. `get_activity` says "by the owner through the chat" for those entries.
The column is server-only: the activity log is not synced, and the apps may show it when they read
the log, but nothing breaks if they don't.

## Limits

- **30 requests a minute and 200 a day per owner**, counted in `assistant_usage` (one row per owner,
  migration 0019) by `assistant_count`. The day is the UTC day. A refused request is not counted, so
  a client that retries too fast can't use up the day. Row security is on with no policy, and only
  the service role may call the function, so no app can read or reset the counts.
- **Gemini's own free-tier limits** come back as `provider_limit`. They are Google's and change now
  and then; the chat's 200 a day sits under them for one owner.

## The model

`GeminiProvider` (`_shared/assistant/geminiProvider.ts`) calls the Gemini API's `generateContent`
with function calling. The model is the constant `GEMINI_MODEL`, `gemini-flash-lite-latest`:
Google's alias for its current light Flash model, which the free tier covers with more calls a
minute and a day than Flash, so a retired version can't break the chat. Pin a named version there if
the alias ever moves to one that behaves worse. Each tool's zod shape is turned into a function
declaration (`_shared/assistant/chatTools.ts`). A model turn with tool calls is sent back exactly as
Gemini gave it, thought signatures included, which newer models require.

### Speed

A message takes at least two model rounds (one picks the tools, one writes the answer), and every
round sends the whole thread and every tool's declaration again. So the chat keeps each round small:

- It offers only its everyday tools (`CHAT_TOOLS` in `chatTools.ts`, 29 of the connector's), each
  with the first sentence of its description. The connector's longer notes are written for Claude.
- A tool with a long input offers the chat only some of it (`CHAT_INPUTS`): `add_habit` and
  `add_goal` take the owner's `line` ("Swim 2 times a week 40 min", "Read 3 books this month") with
  an emoji, a limit or Show on Today, and the goal fed, and the line, read with the bottom bar's
  rules ([composer](composer.md#adding-on-wants-habits-and-goals)), says the rest. `add_want` takes a
  line too. The system prompt says to pass the owner's words as the line, and to ask for a want's
  reason when it has none.
- `assistant_test.ts` keeps every round's declarations under 16,000 characters (about 15,300 now).
- The system prompt names the owner's areas, tags and active projects, so the model doesn't spend a
  round looking them up.
- Thinking is set to minimal: choosing a planner tool needs little of it, and it was most of a
  round's wait.

With these a round takes under a second on the free tier where it took two to five, and about
4,000 prompt tokens where it took 11,000. A tool the chat should offer goes into `CHAT_TOOLS`.

The loop only talks to the `ChatProvider` interface (`_shared/assistant/chatProvider.ts`), so
another model (a local Ollama one on the PC, say) can be added without touching it.

### The key

The key is a Gemini API key from Google AI Studio (free tier), kept as the `GEMINI_API_KEY` function
secret. It never goes in the repository, the apps or a file that is committed. Without it the
function answers `unavailable`.

- Cloud: [cloud-supabase.md, step 8](setup/cloud-supabase.md#8-the-quick-chats-model-key).
- Local stack, for a check with the real model: put `GEMINI_API_KEY=...` in
  `supabase/functions/.env` (ignored by git) and restart `npx supabase functions serve`.

## Tests

No test calls Gemini.

- `_shared/assistant/assistant_test.ts` (part of `deno task test`): every offered tool's input
  becomes a valid Gemini function declaration, no deleting tool is offered, the loop's rounds and its
  limit, Gemini's request and answer forms against a stubbed `fetch`, telling its quota apart from
  other failures, and the prompt.
- `assistant/endpoint_test.ts` drives the running function on the local stack: adding a task,
  adding a habit, a goal and a want from short lines, reading Today, two tool rounds, a delete refused (and not offered), a bad argument, the round
  limit, another owner's rows, and the per-minute and daily limits. It makes its own users, signs
  their sessions with the local stack's development JWT secret, and deletes them after.

**The fake provider.** Each endpoint test request carries its scripted model turns as JSON in the
`x-goalmaker-fake-turns` header, played by `FakeProvider`. The function honours the header only on
the local stack, which it knows by its `SUPABASE_URL` being plain `http://` to `kong` (or localhost);
a hosted project's is always `https://`, so there the header is ignored and Gemini answers
(`_shared/assistant/providerChoice.ts`, with a unit test). Since every endpoint test request carries a
script, a real key in `supabase/functions/.env` is never used by the tests. A header holds only
Latin-1, so a script can't carry text like "Kč"; `fetch` refuses it before the request is sent.

```powershell
cd supabase/functions
npx supabase functions serve                 # in another terminal
$env:GOALMAKER_ASSISTANT_TEST = '1'; npx deno test --allow-read --allow-env --allow-net assistant/endpoint_test.ts
```

## In the apps

Every composer has a switch at its start between quick-add and chat, remembered on the device. On
Windows Ctrl+Shift+Space flips it too, in the main window and in the quick-add box. In chat the
thread shows above the composer with a thinking line while the model works and a Clear button, and
it lives only in memory: closing the app forgets it, and nothing of it is kept on the server. After
an answer the app syncs, so what the chat changed shows at once.

When chat can't run (signed out, offline, or no key on the server) the switch says why, the
owner's choice is kept for later, and the composer quick-adds as before.
