# The Claude connector

GoalMaker works with Claude through a **connector**: a remote MCP server that Claude calls on the
owner's Claude plan, in Claude on the web, the desktop and phone apps, and Claude Code (spec, stories
69 to 77; [ADR 0003](adr/0003-claude-through-an-mcp-connector.md)). There is no Anthropic API account
and no in-app model.

## Adding GoalMaker to Claude

1. In GoalMaker, open Settings, Claude connector, and create the link. It is shown once; copy it.
2. On claude.ai, open Settings, Connectors, Add custom connector, give it a name (GoalMaker) and
   paste the link as the server URL. It then works in every Claude app signed in to that account.
3. In a chat, turn GoalMaker on from the connectors menu and ask about your plans, or pick one of
   its prompts (Plan tomorrow, Weekly review, Monthly review).

The link looks like `https://<project>.supabase.co/functions/v1/connector/<secret>`. Anyone who has
it can read and change your plans until it is revoked, so treat it like a password. **Rotate** makes
a new link and kills the old one; **Revoke** kills it without a new one. Both work from either app.

## What Claude can do

| Tool | What it does |
|---|---|
| `get_today`, `get_tomorrow`, `get_inbox` | The lists as the apps show them, from the same rules |
| `get_task`, `search_tasks`, `list_areas_and_tags` | One task in full; search open and done tasks; areas and tags |
| `add_task`, `update_task` | Day, time, deadline, area, tags, top priority, notes, repeat |
| `complete_task`, `drop_task`, `reopen_task`, `move_task` | A repeating task moves on and back like in the apps |
| `delete_task`, `restore_task` | Deletes are soft and need the owner's yes in the conversation first |
| `add_reminder`, `remove_reminder` | At a local time, or minutes before the task's time |
| `add_step`, `check_step` | A task's checklist |
| `finish_plan_tomorrow` | Records the ritual, which quiets the evening reminder on both devices |
| `save_review_summary`, `get_review_summaries` | A weekly or monthly review's summary, mood and energy |

Prompts: `plan_tomorrow`, `weekly_review` (optionally a week's Monday) and `monthly_review`
(optionally a month like `2026-09`). Each carries the owner's real tasks for the period and the
ritual's steps, and Claude makes the changes through the tools.

Days are the owner's **planning days**: the profile's time zone and day start decide what "today"
is, and both apps keep those in step with the device. Goals, habits and projects get their tools
with their tables (M4 and M5).

## How it is built

- `supabase/functions/connector/index.ts`: the Edge Function, a stateless MCP server over Streamable
  HTTP (the official TypeScript SDK) that answers in JSON. The gateway's JWT check is off for this
  function only (`supabase/config.toml`); the function checks the link itself.
- The secret is hashed (SHA-256) and resolved by `connector_resolve`, which refuses revoked links
  and more than 120 calls a minute (404 and 429). Only the hash is stored
  (`supabase/migrations/0007_connector_links_and_undo.sql`).
- Every tool call then runs in one transaction as the owner: the `authenticated` role with the
  owner's claims, so row security applies exactly as for the apps, and the actor header, so the
  activity log shows the change as made by Claude. The function never touches user tables as the
  service role. Undo works on Claude's changes like on the owner's.
- `supabase/functions/_shared/`: the tools, prompts and data access, reused by the quick chat in M7,
  and `rules/`, the planning rules ported to TypeScript. They run the same vectors in
  `contracts/vectors/` as the Kotlin and C# tests, so Today means the same thing everywhere.

## Testing

```powershell
cd supabase/functions
npx deno task test          # rules against the contract vectors, and unit tests
npx deno task lint
```

The endpoint test drives the running function on the local stack through MCP (it makes its own user
and link and deletes them after):

```powershell
npx supabase functions serve                 # in another terminal
$env:GOALMAKER_CONNECTOR_TEST = '1'; npx deno test --allow-read --allow-env --allow-net connector/endpoint_test.ts
```

CI runs both in the Supabase job.

## A weekly summary routine

A scheduled Claude routine (or any assistant that can use MCP connectors) can leave a summary of the
week waiting in GoalMaker (spec, story 75). Schedule it for Sunday evening with GoalMaker turned on
and a prompt like this:

> Use GoalMaker. Look at this week: call get_today and search_tasks with an empty query for what I
> completed this week, and get_review_summaries for last week's summary. Write a short summary of
> my week in plain words: what I got done, what slipped, and one focus for next week, in at most
> six sentences. Save it with save_review_summary, kind weekly. Don't change any tasks.

The summary is saved as the week's review (the `reviews` table, one row per week, so running it again
replaces it) and shows in the activity log as made by Claude. The apps' review screens arrive in M4.
