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

## Decisions the owner still has to make

- **The key.** The owner makes a Gemini API key in Google AI Studio (free tier) and it goes in as a
  Supabase function secret. Nothing works on the cloud project until then.
- **Who a change is by.** Tasks and wants record `made_by` as `owner` or `claude`, and the activity
  log's actor as `owner`, `claude` or `system`. The chat is not Claude. Either it is recorded as the
  owner (they asked for it in their own app), or as a new `assistant` value (a migration on
  `made_by`, the actor, and both apps' "by Claude" marks). Proposed: the owner, with the activity log
  saying "through the chat".
- **What is kept.** Proposed: the conversation lives only on the device and is gone when the app
  closes; nothing of it is stored on the server.
- **Deletes.** The connector asks Claude to get a yes before deleting. Proposed: the chat never
  deletes; the tools that delete are left out of its tool list.
