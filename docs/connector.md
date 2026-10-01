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
| `get_today`, `get_tomorrow`, `get_inbox` | The lists as the apps show them, from the same rules; Today also lists its habits with where each stands, says how many are left, and counts the ones kept off it |
| `get_task`, `search_tasks`, `list_areas_and_tags` | One task in full; search open and done tasks; areas and tags |
| `get_completed_tasks` | What was completed between two days (this week by default) |
| `add_task`, `update_task` | Day, time, deadline, area, tags, top priority, notes, repeat; a new task also says who made it |
| `add_area`, `update_area`, `delete_area` | An area with its palette color and emoji; archive it or bring it back |
| `add_tag`, `update_tag`, `delete_tag` | A tag; renaming one reaches every task that carries it |
| `complete_task`, `drop_task`, `reopen_task`, `move_task` | A repeating task moves on and back like in the apps |
| `delete_task`, `restore_task` | Deletes are soft and need the owner's yes in the conversation first |
| `add_reminder`, `remove_reminder` | At a local time, or minutes before the task's time |
| `add_step`, `check_step`, `update_step`, `remove_step` | A task's checklist |
| `finish_plan_tomorrow`, `finish_review` | Records a ritual, which quiets its reminder on both devices |
| `save_review_summary`, `get_review_summaries` | A review's summary, mood, energy and the reflections written in it; the summary is also the [Letter](letter.md) |
| `get_review_digest` | One week or month in one JSON answer, for the Letter routine: done, left and overdue, goals, habits, the board, triggers, the review and last letter, the next period, wants and Tally's time. The week or month holding yesterday by default |
| `get_time_tally` | Where time went on the phone and the PC ([Tally](tally.md)), by category, project or device, this week by default; never by app, since apps never leave the device |
| `get_goals`, `add_goal`, `update_goal` | The goals of a period with their pace and the goal each feeds, the ones that need the owner first; new or changed ones, a new one also from a short `line` |
| `set_goal_status`, `log_goal_amount`, `delete_goal` | Mark a goal done, dropped or open again; log an amount like "+5 km" |
| `get_habits`, `check_in_habit`, `skip_habit` | Habits in the Habits page's groups with where each stands, its streak and the goal it serves, and "not on Today" for one kept off Today; check one in, or skip a period |
| `add_habit`, `update_habit`, `delete_habit` | A habit's cadence, measure, target, direction, the goal it feeds and whether it shows on Today (`show_on_today`); a new one also from a short `line` |
| `pause_habit`, `resume_habit` | A stretch of days that neither breaks a streak nor counts; one pause at a time |
| `get_projects`, `get_project_board` | The projects with what is open in each; one project's four columns in board order, all of them or only the owner's or Claude's items |
| `find_project` | The project a repository URL or a working folder belongs to (story 76) |
| `create_project`, `create_milestone` | A project with its area, repository, folder and milestones; a milestone on one that already exists |
| `add_project_item`, `update_project_item` | An item with its type, priority, milestone, column and who made it; its project can change |
| `move_project_item` | Moves an item between columns, which finishes or reopens the task with it |
| `update_project`, `delete_project` | A project's own fields, its status, or the project itself; its items stay |
| `update_milestone`, `delete_milestone` | Renames or removes a milestone; the items that carried it stay |
| `get_activity`, `undo_change` | The latest changes with who made each (owner, Claude or GoalMaker), and undo |
| `get_settings`, `update_settings` | The time zone and day start every planning day is worked out from |
| `get_calendar` | A stretch of days with what is planned, what is due, and where a repeat would come round |
| `get_wants` | The wants that are ready, cooling or decided, each with its reason, price, last price check and note |
| `add_want`, `update_want` | A want with its reason, price, link and area; its cooldown comes from the owner's thresholds unless picked, and never moves after; a new one also from a short `line` |
| `decide_want` | Bought or dropped with a note, or reopened; only what the owner decided in the conversation |
| `record_price_check` | The price Claude found with its own web search, where, and the alternatives; GoalMaker never fetches from a shop |

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

Wants are decided with the owner, not for them ([wants](wants.md)). For example:

> Use GoalMaker. Which of my wants are ready? Check the prices and ask me about each.

Claude reads the ready wants, looks each price up with its own web search, records what it found
with `record_price_check`, and asks whether the owner still wants it before `decide_want` marks it
bought or dropped. A want Claude adds says "by Claude" in both apps.

Habits and goals read the way their cards do, from the same rules (`standings`, `groups` and
`allDone` in `contracts/vectors/habits.json`, the pace and its order in `goals.json`):

```text
Today is Thursday 1 October 2026: 0 of 1 done, 1 habit left.
...
Habits, 1 habit left:
- 💧 Water · every day · left · 3 of 8 glasses today (habit id ...)
- Floss · every day · done · 1-day streak (habit id ...)
- Read · every day · skipped (habit id ...)
- Swim · 2 times a week · done · 1 of 2 this week (habit id ...)
- Snacks · every day · limit · 1 of at most 2 today (habit id ...)
1 more habit is due today but kept off Today; get_habits lists every habit.
```

A habit is done, left, skipped, paused, a limit (never done and never left, so it never counts as
left), or not due today. "Left" counts only the left habits on Today, the same number the apps show.
`get_habits` puts them under **Every day**, **Weekly** and **Limits** (and **Archived** when asked
for), with the goal each serves. `get_goals` gives each open goal its pace, **On track**, **Behind
by 6 km**, **Needs you** or **Hit**, says what it feeds, and lists each period's goals that need the
owner first; each horizon's line counts the goals hit and the ones that need the owner.

`add_want`, `add_habit` and `add_goal` also take a `line`, read with the bottom bar's rules
([composer](composer.md#adding-on-wants-habits-and-goals)): `Swim 2 times a week 40 min`,
`Read 3 books this month`, `Kindle 3290 Kč wait 2 weeks because I read on the train`. Fields given
apart win over what the line says, and every input from before still works.

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

## The weekly letter routine

A scheduled Claude routine can leave a letter about the week waiting in GoalMaker (spec, stories 75
and 100): it reads the week with `get_review_digest`, writes the letter, and saves it with
`save_review_summary`. The routine, its prompt and the owner's one-time setup are in
[the Letter](letter.md). The letter is saved as the week's review (one row per week, so running it
again replaces it), shows in the activity log as made by Claude, and both apps open the review on it.
