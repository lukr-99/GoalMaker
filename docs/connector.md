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
| `get_completed_tasks` | What was completed between two days (this week by default) |
| `add_task`, `update_task` | Day, time, deadline, area, tags, top priority, notes, repeat |
| `add_area`, `update_area`, `delete_area` | An area with its palette color and emoji; archive it or bring it back |
| `add_tag`, `update_tag`, `delete_tag` | A tag; renaming one reaches every task that carries it |
| `complete_task`, `drop_task`, `reopen_task`, `move_task` | A repeating task moves on and back like in the apps |
| `delete_task`, `restore_task` | Deletes are soft and need the owner's yes in the conversation first |
| `add_reminder`, `remove_reminder` | At a local time, or minutes before the task's time |
| `add_step`, `check_step`, `update_step`, `remove_step` | A task's checklist |
| `finish_plan_tomorrow`, `finish_review` | Records a ritual, which quiets its reminder on both devices |
| `save_review_summary`, `get_review_summaries` | A review's summary, mood, energy and the reflections written in it |
| `get_goals`, `add_goal`, `update_goal` | The goals of a period with where each stands, and new or changed ones |
| `set_goal_status`, `log_goal_amount`, `delete_goal` | Mark a goal done, dropped or open again; log an amount like "+5 km" |
| `get_habits`, `check_in_habit`, `skip_habit` | Habits with today's state and streak; check one in, or skip a period |
| `add_habit`, `update_habit`, `delete_habit` | A habit's cadence, measure, target, direction and the goal it feeds |
| `pause_habit`, `resume_habit` | A stretch of days that neither breaks a streak nor counts |
| `get_projects`, `get_project_board` | The projects with what is open in each; one project's four columns in board order |
| `find_project` | The project a repository URL or a working folder belongs to (story 76) |
| `create_project`, `create_milestone` | A project with its area, repository, folder and milestones; a milestone on one that already exists |
| `add_project_item`, `update_project_item` | An item with its type, priority, milestone and column; its project can change |
| `move_project_item` | Moves an item between columns, which finishes or reopens the task with it |
| `update_project`, `delete_project` | A project's own fields, its status, or the project itself; its items stay |
| `update_milestone`, `delete_milestone` | Renames or removes a milestone; the items that carried it stay |
| `get_activity`, `undo_change` | The latest changes with who made each, and undo over the apps' own rules |
| `get_settings`, `update_settings` | The time zone and day start every planning day is worked out from |
| `get_calendar` | A stretch of days with what is planned, what is due and what carries a reminder |

Prompts: `plan_tomorrow`, `weekly_review` (optionally a week's Monday) and `monthly_review`
(optionally a month like `2026-09`). Each carries the owner's real tasks for the period and the
ritual's steps, and Claude makes the changes through the tools. The two review prompts also carry the
period's goals with their progress, how each habit held up, and what the period's data asks about (the
same triggers the apps' reactive prompts use, [reviews](reviews.md)).

Days are the owner's **planning days**: the profile's time zone and day start decide what "today"
is, and both apps keep those in step with the device.

A **project item is an ordinary task**, so `update_task`, reminders and steps work on it as on
anything else, and an item with a planned day turns up in Today with its project's name after it.
The board and the list never disagree: moving an item to Done completes the task, completing a task
moves it to Done, and reopening a done item puts it back in To do ([projects](projects.md)).

A project is named by whatever is at hand: its id, its repository URL (https or ssh, with or
without `.git`), the folder being worked in (a folder inside the project's own counts), or its
name. That is what lets Claude Code drop an idea into the right backlog from the checkout it is in
(story 76):

> Use GoalMaker. Add an idea to the project for the folder I'm working in: "Cache the release
> manifest between checks", priority high, milestone M6.

A goal or a habit Claude touches is the owner's own row, so it turns up on both apps after a sync and
in the activity log as made by Claude. A check-in is named after its habit and day, exactly as the
apps name it, so checking in here and checking in on the phone are one row, never two.

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

> Use GoalMaker. Look at my week: get_completed_tasks for what I finished, get_today for what is
> still open or overdue, and get_review_summaries for last week's summary. Write a short summary of
> my week in plain words: what I got done, what slipped, and one focus for next week, in at most
> six sentences. Save it with save_review_summary, kind weekly. Don't change any tasks.

The summary is saved as the week's review (the `reviews` table, one row per week, so running it again
replaces it) and shows in the activity log as made by Claude. The apps' review screens arrive in M4.
