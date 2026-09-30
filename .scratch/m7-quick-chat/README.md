# M7: quick chat

A chat in the composer: the owner types a request in plain words ("move everything from today to
Friday", "what's left for the GoalMaker project?") and a model answers and acts through the same
tools the Claude connector serves. The composer switches between quick-add, as today, and chat
(spec, "Quick chat (M7)"; roadmap, M7).

- `assistant`, a Supabase Edge Function, runs the tool loop over `supabase/functions/_shared/tools`
  as the signed-in owner, with the Gemini free tier behind a provider interface.
- Both apps get the switch and a chat view in the composer.

## Issues

1. [01-assistant-function.md](01-assistant-function.md): the Edge Function, the provider interface
   and Gemini, the tool loop, limits.
2. [02-chat-on-android.md](02-chat-on-android.md): the switch and the chat on the phone.
3. [03-chat-on-windows.md](03-chat-on-windows.md): the same on the PC.

## Decisions (owner, 2026-09-30)

- **The key.** The owner makes a Gemini API key in Google AI Studio (free tier) and it goes in as the
  `GEMINI_API_KEY` Supabase function secret. Nothing works on the cloud project until then.
- **Who a change is by.** The owner: they asked for it in their own app. The activity log notes that
  it came through the chat. No new `made_by` value.
- **What is kept.** The conversation lives only on the device and is gone when the app closes;
  nothing of it is stored on the server.
- **Deletes.** The chat never deletes; the tools that delete are left out of its tool list.

## The call

`POST /functions/v1/assistant` with the owner's session (`Authorization: Bearer <access token>`) and
`{"messages": [{"role": "user" | "model", "text": "..."}]}`, the whole thread so far, last one the
owner's. The answer is `200 {"text": "..."}`. A failure is `{"error": code, "message": "..."}` with
code `unavailable` (503, no key on the server), `rate_limited` (429, our limits), `provider_limit`
(429, Gemini's free tier is used up), `bad_request` (400) or `failed` (502).
