// Drives the running connector on the local stack through its MCP endpoint (spec, Testing: connector).
// Runs when GOALMAKER_CONNECTOR_TEST=1, after `npx supabase start` (and `functions serve` if the stack
// doesn't serve functions). It makes its own user and link in the database and deletes them after.
import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import { postgres } from "../_shared/deps.ts";

const enabled = Deno.env.get("GOALMAKER_CONNECTOR_TEST") === "1";
const api = Deno.env.get("GOALMAKER_API_URL") ?? "http://127.0.0.1:55321";
const dbUrl = Deno.env.get("GOALMAKER_DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:55322/postgres";

const OWNER = "c0ffee00-0000-4000-8000-000000000001";
const STRANGER = "c0ffee00-0000-4000-8000-000000000002";

// deno-lint-ignore no-explicit-any
type Json = any;

class Client {
  private next = 1;

  constructor(readonly url: string) {}

  async post(method: string, params: Json = {}): Promise<Response> {
    return await fetch(this.url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json, text/event-stream",
        "mcp-protocol-version": "2025-06-18",
      },
      body: JSON.stringify({ jsonrpc: "2.0", id: this.next++, method, params }),
    });
  }

  async call(method: string, params: Json = {}): Promise<Json> {
    const response = await this.post(method, params);
    assertEquals(response.status, 200, await response.clone().text());
    const body = await response.json();
    assert(body.error === undefined, JSON.stringify(body.error));
    return body.result;
  }

  async tool(name: string, args: Json = {}): Promise<{ text: string; isError: boolean }> {
    const result = await this.call("tools/call", { name, arguments: args });
    return { text: result.content.map((part: Json) => part.text).join("\n"), isError: result.isError === true };
  }
}

Deno.test({
  name: "the connector serves GoalMaker's tools as the owner",
  ignore: !enabled,
  sanitizeResources: false,
  sanitizeOps: false,
  fn: async (t) => {
    const sql = postgres(dbUrl, { max: 1 });
    try {
      await sql`delete from auth.users where id in (${OWNER}, ${STRANGER})`;
      for (
        const [id, email] of [[OWNER, "connector-owner@example.test"], [STRANGER, "connector-stranger@example.test"]]
      ) {
        await sql`
          insert into auth.users (id, instance_id, aud, role, email)
          values (${id}, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', ${email})`;
      }
      await sql`update public.profiles set time_zone = 'Europe/Prague', day_rollover_hour = 4 where id = ${OWNER}`;
      await sql`
        insert into public.tasks (id, owner_id, title)
        values ('c0ffee00-0000-4000-8000-0000000000aa', ${STRANGER}, 'The stranger''s secret task')`;
      const secret = await sql.begin(async (db) => {
        await db`select set_config('request.jwt.claims', ${
          JSON.stringify({ sub: OWNER, role: "authenticated" })
        }, true)`;
        await db`set local role authenticated`;
        return (await db`select public.create_connector_link() as secret`)[0].secret as string;
      });
      const client = new Client(`${api}/functions/v1/connector/${secret}`);

      await t.step("initialize names the server", async () => {
        const result = await client.call("initialize", {
          protocolVersion: "2025-06-18",
          capabilities: {},
          clientInfo: { name: "endpoint-test", version: "1" },
        });
        assertEquals(result.serverInfo.name, "goalmaker");
      });

      await t.step("the tools and prompts are listed", async () => {
        const tools = (await client.call("tools/list")).tools.map((tool: Json) => tool.name);
        for (const name of ["get_today", "add_task", "complete_task", "delete_task", "finish_plan_tomorrow"]) {
          assert(tools.includes(name), `${name} in ${tools}`);
        }
        const prompts = (await client.call("prompts/list")).prompts.map((prompt: Json) => prompt.name);
        assertEquals(prompts.sort(), ["monthly_review", "plan_tomorrow", "weekly_review"]);
      });

      let taskId = "";
      await t.step("a task added through the connector is the owner's and logged as Claude's", async () => {
        const added = await client.tool("add_task", {
          title: "Book the dentist",
          day: "today",
          time: "9:30",
          area: "Health",
          tags: ["errand"],
          top_priority: true,
        });
        assert(!added.isError, added.text);
        taskId = /\(id ([0-9a-f-]{36})\)/.exec(added.text)![1];
        const [row] = await sql`
          select owner_id::text, planned_time::text, top_priority from public.tasks where id = ${taskId}`;
        assertEquals(row, { owner_id: OWNER, planned_time: "09:30:00", top_priority: true });
        const [log] = await sql`
          select actor from public.activity_log where entity = 'tasks' and entity_id = ${taskId} and action = 'create'`;
        assertEquals(log.actor, "claude");
        const [area] = await sql`select name, color from public.areas where owner_id = ${OWNER}`;
        assertEquals(area, { name: "Health", color: "violet" });
      });

      await t.step("Today shows it and never the stranger's rows", async () => {
        const today = await client.tool("get_today");
        assertStringIncludes(today.text, "Top priorities:");
        assertStringIncludes(today.text, "Book the dentist · top priority · 09:30 · @Health · #errand");
        assert(!today.text.includes("stranger"), today.text);
        const search = await client.tool("search_tasks", { query: "secret" });
        assertEquals(search.text, 'Nothing matches "secret".');
      });

      await t.step("a repeating task moves on when completed, and back when reopened", async () => {
        const added = await client.tool("add_task", { title: "Water the plants", day: "today", repeat: "FREQ=DAILY" });
        const id = /\(id ([0-9a-f-]{36})\)/.exec(added.text)![1];
        const done = await client.tool("complete_task", { id });
        assertStringIncludes(done.text, "Next occurrence:");
        const completed = await client.tool("get_completed_tasks");
        assertStringIncludes(completed.text, "[x] Water the plants");
        const [next] = await sql`
          select count(*)::int as open from public.tasks
          where owner_id = ${OWNER} and series_id = ${id} and status = 'open' and deleted_at is null`;
        assertEquals(next.open, 1);
        await client.tool("reopen_task", { id });
        const [after] = await sql`
          select count(*)::int as open from public.tasks
          where owner_id = ${OWNER} and series_id = ${id} and status = 'open' and deleted_at is null`;
        assertEquals(after.open, 1, "the reopened one, and its next occurrence taken back");
      });

      await t.step("a delete needs the owner's yes", async () => {
        const refused = await client.tool("delete_task", { id: taskId, confirmed: false });
        assert(refused.isError);
        assertStringIncludes(refused.text, 'whether to delete "Book the dentist"');
        const [live] = await sql`select deleted_at from public.tasks where id = ${taskId}`;
        assertEquals(live.deleted_at, null);
        const deleted = await client.tool("delete_task", { id: taskId, confirmed: true });
        assert(!deleted.isError, deleted.text);
        const restored = await client.tool("restore_task", { id: taskId });
        assertStringIncludes(restored.text, "Restored:");
      });

      await t.step("a reminder is set in the owner's time zone", async () => {
        const set = await client.tool("add_reminder", { task_id: taskId, at: "2026-12-24 18:00" });
        assert(!set.isError, set.text);
        const [reminder] = await sql`
          select to_char(fire_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI') as utc from public.reminders
          where task_id = ${taskId}`;
        assertEquals(reminder.utc, "2026-12-24 17:00");
      });

      await t.step("the Plan tomorrow prompt carries the owner's tasks and the ritual can be recorded", async () => {
        const prompt = await client.call("prompts/get", { name: "plan_tomorrow" });
        assertStringIncludes(prompt.messages[0].content.text, "Book the dentist");
        const recorded = await client.tool("finish_plan_tomorrow");
        assert(!recorded.isError, recorded.text);
        const [run] =
          await sql`select outcome from public.ritual_runs where owner_id = ${OWNER} and ritual = 'plan_tomorrow'`;
        assertEquals(run.outcome, "done");
      });

      await t.step("a weekly summary is saved for its week and read back", async () => {
        const saved = await client.tool("save_review_summary", {
          kind: "weekly",
          period: "2026-09-17",
          summary: "Shipped the connector.",
          mood: 4,
        });
        assert(!saved.isError, saved.text);
        assertStringIncludes(saved.text, "Monday 14 September 2026");
        const again = await client.tool("save_review_summary", {
          kind: "weekly",
          period: "2026-09-18",
          summary: "Shipped the connector and the palette.",
        });
        assert(!again.isError, again.text);
        const [review] = await sql`
          select period_start::text, summary, mood from public.reviews where owner_id = ${OWNER} and kind = 'weekly'`;
        assertEquals(review, {
          period_start: "2026-09-14",
          summary: "Shipped the connector and the palette.",
          mood: 4,
        });
        const read = await client.tool("get_review_summaries", { kind: "weekly" });
        assertStringIncludes(read.text, "weekly review, from Monday 14 September 2026, mood 4/5:");
      });

      await t.step("a goal is set, counted and marked, and a habit feeds it", async () => {
        const added = await client.tool("add_goal", {
          title: "Run 20 km",
          horizon: "week",
          day: "2026-09-18",
          target: 20,
          unit: "km",
          emoji: "\u{1F3C3}",
        });
        assert(!added.isError, added.text);
        const id = /\(goal id ([0-9a-f-]{36})\)/.exec(added.text)![1];
        assertStringIncludes(added.text, "0 of 20 km");

        const logged = await client.tool("log_goal_amount", { id, amount: 8, day: "2026-09-18" });
        assert(!logged.isError, logged.text);
        assertStringIncludes(logged.text, "8 of 20 km");

        const changed = await client.tool("update_goal", { id, target: 10 });
        assert(!changed.isError, changed.text);
        assertStringIncludes(changed.text, "8 of 10 km · 80%");

        const goals = await client.tool("get_goals", { day: "2026-09-18" });
        assertStringIncludes(goals.text, "Week goals:");
        assertStringIncludes(goals.text, "Run 20 km");

        const marked = await client.tool("set_goal_status", { id, status: "done" });
        assert(!marked.isError, marked.text);
        const [row] = await sql`
          select status, target, period_start::text, completed_at is not null as stamped
          from public.goals where id = ${id}`;
        assertEquals(row, { status: "done", target: 10, period_start: "2026-09-14", stamped: true });
        const [log] = await sql`
          select actor from public.activity_log where entity = 'goals' and entity_id = ${id} and action = 'create'`;
        assertEquals(log.actor, "claude");
      });

      await t.step("a check-in Claude makes is the owner's own row, logged as Claude's", async () => {
        const habitId = "c0ffee00-0000-4000-8000-0000000000b1";
        await sql`
          insert into public.habits (id, owner_id, name, cadence, measure, target, unit, starts_on)
          values (${habitId}, ${OWNER}, 'Water', 'daily', 'count', 8, 'glasses', '2026-09-01')`;

        const habits = await client.tool("get_habits", { day: "2026-09-18" });
        assertStringIncludes(habits.text, "Water · every day · 0 of 8 glasses");

        const first = await client.tool("check_in_habit", { id: habitId, day: "2026-09-18", amount: 3 });
        assert(!first.isError, first.text);
        const second = await client.tool("check_in_habit", { id: habitId, day: "2026-09-18", amount: 5 });
        assertStringIncludes(second.text, "at 8 glasses");
        assertStringIncludes(second.text, "8 of 8 glasses this period · done");

        const [checkin] = await sql`
          select owner_id::text, value, skipped from public.habit_checkins
          where habit_id = ${habitId} and day = '2026-09-18'`;
        assertEquals(checkin, { owner_id: OWNER, value: 8, skipped: false });
        const [log] = await sql`
          select actor, action from public.activity_log
          where entity = 'habit_checkins' and actor = 'claude' order by id desc limit 1`;
        assertEquals(log.actor, "claude");

        const skipped = await client.tool("skip_habit", { id: habitId, day: "2026-09-17" });
        assert(!skipped.isError, skipped.text);
        const [rest] = await sql`
          select skipped from public.habit_checkins where habit_id = ${habitId} and day = '2026-09-17'`;
        assertEquals(rest.skipped, true);

        const strangers = await client.tool("check_in_habit", { id: "c0ffee00-0000-4000-8000-0000000000ff" });
        assert(strangers.isError, strangers.text);
      });

      await t.step("a project's board is read, added to and moved through the connector", async () => {
        const projectId = "c0ffee00-0000-4000-8000-0000000000c1";
        const milestoneId = "c0ffee00-0000-4000-8000-0000000000c2";
        await sql`
          insert into public.projects (id, owner_id, name, description, status, repository_url, local_folder)
          values (${projectId}, ${OWNER}, 'GoalMaker', 'The planner itself', 'active',
                  'https://github.com/owner/goalmaker.git', ${"F:\\GoalMaker"})`;
        await sql`
          insert into public.project_milestones (id, owner_id, project_id, name)
          values (${milestoneId}, ${OWNER}, ${projectId}, 'M5')`;

        const found = await client.tool("find_project", { folder: "F:\\GoalMaker\\supabase\\functions" });
        assert(!found.isError, found.text);
        assertStringIncludes(found.text, `(project id ${projectId})`);
        assertStringIncludes(found.text, "Milestones: M5");

        const idea = await client.tool("add_project_item", {
          project: "git@github.com:owner/goalmaker.git",
          title: "Share to GoalMaker",
          type: "idea",
          priority: "high",
          milestone: "M5",
        });
        assert(!idea.isError, idea.text);
        assertStringIncludes(idea.text, "Added to GoalMaker, Backlog:");
        const itemId = /\(id ([0-9a-f-]{36})\)/.exec(idea.text)![1];
        const [item] = await sql`
          select project_id::text, item_type, board_column, priority, milestone_id::text
          from public.tasks where id = ${itemId}`;
        assertEquals(item, {
          project_id: projectId,
          item_type: "idea",
          board_column: "backlog",
          priority: "high",
          milestone_id: milestoneId,
        });
        const [log] = await sql`
          select actor from public.activity_log where entity = 'tasks' and entity_id = ${itemId} and action = 'create'`;
        assertEquals(log.actor, "claude");

        const board = await client.tool("get_project_board", { project: "GoalMaker" });
        assertStringIncludes(board.text, "Backlog (1):");
        assertStringIncludes(board.text, "[ ] Share to GoalMaker · idea · high · M5");
        assertStringIncludes(board.text, "To do: nothing.");

        const moved = await client.tool("move_project_item", { id: itemId, column: "done" });
        assert(!moved.isError, moved.text);
        assertStringIncludes(moved.text, "Moved to Done in GoalMaker:");
        const [done] = await sql`select status, board_column from public.tasks where id = ${itemId}`;
        assertEquals(done, { status: "done", board_column: "done" });

        await client.tool("reopen_task", { id: itemId });
        const [reopened] = await sql`select status, board_column from public.tasks where id = ${itemId}`;
        assertEquals(reopened, { status: "open", board_column: "todo" }, "a done item reopened goes back to To do");

        const bug = await client.tool("add_project_item", {
          project: projectId,
          title: "The ring flickers",
          type: "bug",
          day: "today",
        });
        assert(!bug.isError, bug.text);
        assertStringIncludes(bug.text, "Added to GoalMaker, To do:");
        const today = await client.tool("get_today");
        assertStringIncludes(today.text, "The ring flickers · +GoalMaker · bug");

        const out = await client.tool("update_project_item", { id: itemId, project: "" });
        assert(!out.isError, out.text);
        assertStringIncludes(out.text, "a plain task again");
        const [plain] = await sql`
          select project_id, board_column, milestone_id from public.tasks where id = ${itemId}`;
        assertEquals(plain, { project_id: null, board_column: null, milestone_id: null });

        const projects = await client.tool("get_projects");
        assertStringIncludes(projects.text, "GoalMaker · active · 1 open of 1");
        assertStringIncludes(projects.text, "folder F:\\GoalMaker");

        const missing = await client.tool("add_project_item", { project: "F:\\Somewhere", title: "Nowhere" });
        assert(missing.isError, missing.text);
        assertStringIncludes(missing.text, "The owner's projects are: GoalMaker.");
      });

      await t.step("a project and its milestones are created through the connector", async () => {
        const made = await client.tool("create_project", {
          name: "Relay",
          description: "Phone as a Stream Deck",
          area: "Side projects",
          repository: "https://github.com/owner/relay",
          folder: "F:\\Relay",
          notes: "The agent is the brain.",
          milestones: ["M0", "M1"],
        });
        assert(!made.isError, made.text);
        assertStringIncludes(made.text, "Relay · active · @Side projects · no items yet");
        assertStringIncludes(made.text, "Milestones: M0");
        const madeId = /\(project id ([0-9a-f-]{36})\)/.exec(made.text)![1];
        const [row] = await sql`
          select name, description, status, repository_url, local_folder, notes
          from public.projects where id = ${madeId}`;
        assertEquals(row, {
          name: "Relay",
          description: "Phone as a Stream Deck",
          status: "active",
          repository_url: "https://github.com/owner/relay",
          local_folder: "F:\\Relay",
          notes: "The agent is the brain.",
        });
        const [log] = await sql`
          select actor from public.activity_log
          where entity = 'projects' and entity_id = ${madeId} and action = 'create'`;
        assertEquals(log.actor, "claude");
        const milestones = await sql`
          select name from public.project_milestones where project_id = ${madeId} order by position`;
        assertEquals(milestones.map((one: Json) => one.name), ["M0", "M1"]);

        // What the repository and the folder are for: a checkout reaches its own project.
        const reached = await client.tool("find_project", { folder: "F:\\Relay\\agent\\src" });
        assertStringIncludes(reached.text, `(project id ${madeId})`);

        const milestone = await client.tool("create_milestone", { project: "F:\\Relay", name: "M2" });
        assert(!milestone.isError, milestone.text);
        assertStringIncludes(milestone.text, "Added M2 to Relay");
        const twice = await client.tool("create_milestone", { project: madeId, name: "m2" });
        assert(twice.isError, twice.text);

        // A name, a repository or a folder already taken would make find_project a toss-up.
        for (
          const clash of [
            { name: "relay", folder: "F:\\Elsewhere" },
            { name: "Relay agent", repository: "git@github.com:owner/relay.git" },
            { name: "Relay agent", folder: "F:\\Relay\\" },
          ]
        ) {
          const refused = await client.tool("create_project", clash);
          assert(refused.isError, `${JSON.stringify(clash)} should have clashed: ${refused.text}`);
        }

        // A project inside another project's folder is fine, and the deepest folder wins.
        const nested = await client.tool("create_project", { name: "Relay agent", folder: "F:\\Relay\\agent" });
        assert(!nested.isError, nested.text);
        const nestedId = /\(project id ([0-9a-f-]{36})\)/.exec(nested.text)![1];
        const inner = await client.tool("find_project", { folder: "F:\\Relay\\agent\\src" });
        assertStringIncludes(inner.text, `(project id ${nestedId})`);

        const nameless = await client.tool("create_project", { name: "  " });
        assert(nameless.isError, nameless.text);
        assertStringIncludes(nameless.text, "A project needs a name.");
      });

      await t.step("areas, tags and steps are managed through the connector", async () => {
        const area = await client.tool("add_area", { name: "Deep work", color: "teal" });
        assert(!area.isError, area.text);
        assertStringIncludes(area.text, "@Deep work · teal");
        const areaId = /\(area id ([0-9a-f-]{36})\)/.exec(area.text)![1];
        const again = await client.tool("add_area", { name: "deep work" });
        assertStringIncludes(again.text, areaId, "the same name gives back the same area");

        const recolored = await client.tool("update_area", {
          id: areaId,
          name: "Focus",
          color: "amber",
          archived: true,
        });
        assertStringIncludes(recolored.text, "@Focus · amber");
        assertStringIncludes(recolored.text, "archived");
        const offPalette = await client.tool("update_area", { id: areaId, color: "burgundy" });
        assert(offPalette.isError, offPalette.text);
        await client.tool("update_area", { id: areaId, archived: false });

        const tag = await client.tool("add_tag", { name: "spec" });
        const tagId = /\(tag id ([0-9a-f-]{36})\)/.exec(tag.text)![1];
        assertStringIncludes((await client.tool("update_tag", { id: tagId, name: "specs" })).text, "#specs");

        const task = await client.tool("add_task", { title: "Write the spec", area: "Focus", tags: ["specs"] });
        const taskId = /\(id ([0-9a-f-]{36})\)/.exec(task.text)![1];
        const step = await client.tool("add_step", { task_id: taskId, title: "Draft it" });
        const stepId = /\(step id ([0-9a-f-]{36})\)/.exec(step.text)![1];
        assertStringIncludes(
          (await client.tool("update_step", { step_id: stepId, title: "Draft the outline" })).text,
          "Draft the outline",
        );
        assert(!(await client.tool("remove_step", { step_id: stepId })).isError);

        await client.tool("delete_area", { id: areaId });
        await client.tool("delete_tag", { id: tagId });
        const [bare] = await sql`select title, area_id from public.tasks where id = ${taskId}`;
        const [links] = await sql`
          select count(*)::int as n from public.task_tags where task_id = ${taskId} and deleted_at is null`;
        assertEquals(
          { title: bare.title, area_id: bare.area_id, tags: links.n },
          { title: "Write the spec", area_id: null, tags: 0 },
          "a deleted area and tag leave the task itself alone",
        );
      });

      await t.step("habits are made, edited, paused and deleted through the connector", async () => {
        const gym = await client.tool("add_habit", {
          name: "Gym",
          cadence: "weekdays",
          weekdays: ["mon", "wed", "fri"],
        });
        assert(!gym.isError, gym.text);
        const gymId = /\(habit id ([0-9a-f-]{36})\)/.exec(gym.text)![1];
        const [mask] = await sql`select cadence, weekdays from public.habits where id = ${gymId}`;
        assertEquals(mask, { cadence: "weekdays", weekdays: 21 }, "weekday names become the mask they are kept as");

        for (
          const wrong of [
            { name: "Stretch", cadence: "weekdays" },
            { name: "Water", measure: "count" },
            { name: "Snacks", cadence: "per_week", times: 2, measure: "count", target: 2, direction: "at_most" },
          ]
        ) {
          const refused = await client.tool("add_habit", wrong);
          assert(refused.isError, `${JSON.stringify(wrong)} should not fit: ${refused.text}`);
        }

        const edited = await client.tool("update_habit", {
          id: gymId,
          name: "Gym session",
          measure: "count",
          target: 3,
        });
        assert(!edited.isError, edited.text);
        assertStringIncludes(edited.text, "Gym session");

        assertStringIncludes(
          (await client.tool("pause_habit", { id: gymId, from: "2026-10-01", until: "2026-10-10" })).text,
          "paused from",
        );
        for (
          const overlapping of [
            { id: gymId, from: "2026-10-05", until: "2026-10-20" },
            { id: gymId, from: "2026-09-01" },
          ]
        ) {
          const again = await client.tool("pause_habit", overlapping);
          assert(again.isError, `a pause over paused days should be refused: ${again.text}`);
        }
        const [onlyOne] = await sql`select count(*)::int as n from public.habit_pauses where habit_id = ${gymId}`;
        assertEquals(onlyOne.n, 1, "a refused pause writes nothing");

        assertStringIncludes(
          (await client.tool("resume_habit", { id: gymId, day: "2026-10-05" })).text,
          "counts again from",
        );
        const [pause] = await sql`select ends_on::text from public.habit_pauses where habit_id = ${gymId}`;
        assertEquals(pause.ends_on, "2026-10-04", "the pause ends the day before the habit counts again");

        await client.tool("check_in_habit", { id: gymId, amount: 1 });
        assert(!(await client.tool("delete_habit", { id: gymId })).isError);
        const [left] = await sql`
          select count(*)::int as n from public.habit_checkins where habit_id = ${gymId} and deleted_at is null`;
        assertEquals(left.n, 0, "a deleted habit takes its check-ins with it");
      });

      await t.step("a project is edited and deleted, and its items stay", async () => {
        const board = await client.tool("find_project", { folder: "F:\\Relay" });
        const projectId = /\(project id ([0-9a-f-]{36})\)/.exec(board.text)![1];
        const milestoneId = /\(milestone id ([0-9a-f-]{36})\)/.exec(board.text)![1];
        const item = await client.tool("add_project_item", { project: projectId, title: "Pair over mDNS" });
        const itemId = /\(id ([0-9a-f-]{36})\)/.exec(item.text)![1];

        const paused = await client.tool("update_project", {
          project: projectId,
          name: "Relay deck",
          status: "paused",
        });
        assert(!paused.isError, paused.text);
        assertStringIncludes(paused.text, "Relay deck · paused");
        const clash = await client.tool("update_project", { project: "GoalMaker", name: "relay deck" });
        assert(clash.isError, clash.text);

        assertStringIncludes(
          (await client.tool("update_milestone", { id: milestoneId, name: "M0 spine" })).text,
          "M0 spine",
        );
        assert(!(await client.tool("delete_milestone", { id: milestoneId })).isError);

        const dropped = await client.tool("delete_project", { project: projectId });
        assertStringIncludes(dropped.text, "stayed as plain tasks");
        const [plain] = await sql`
          select title, project_id, board_column, milestone_id, deleted_at
          from public.tasks where id = ${itemId}`;
        assertEquals(
          plain,
          { title: "Pair over mDNS", project_id: null, board_column: null, milestone_id: null, deleted_at: null },
          "a deleted project leaves its items behind as plain tasks",
        );
      });

      await t.step("goals nest, reviews keep their reflections, and rituals are recorded", async () => {
        const month = await client.tool("add_goal", { title: "Ship the connector", horizon: "month" });
        const monthId = /\(goal id ([0-9a-f-]{36})\)/.exec(month.text)![1];
        const week = await client.tool("add_goal", { title: "Land the tools", horizon: "week", parent: monthId });
        const weekId = /\(goal id ([0-9a-f-]{36})\)/.exec(week.text)![1];
        const [child] = await sql`select parent_id::text from public.goals where id = ${weekId}`;
        assertEquals(child.parent_id, monthId, "a goal sits under a wider one");
        const loop = await client.tool("update_goal", { id: monthId, parent: weekId });
        assert(loop.isError, "a wider goal cannot sit under a narrower one: " + loop.text);
        const far = await client.tool("add_goal", { title: "A week in 2027", horizon: "week", day: "2027-03-01" });
        const farId = /\(goal id ([0-9a-f-]{36})\)/.exec(far.text)![1];
        const apart = await client.tool("update_goal", { id: farId, parent: monthId });
        assert(apart.isError, "periods that do not overlap cannot be linked: " + apart.text);
        await client.tool("delete_goal", { id: farId });
        assert(!(await client.tool("delete_goal", { id: weekId })).isError);

        const saved = await client.tool("save_review_summary", {
          kind: "weekly",
          summary: "The connector grew up.",
          mood: 4,
          reflections: [{ prompt: "reviews/what_went_well", answer: "Every tool landed." }],
        });
        assert(!saved.isError, saved.text);
        const read = await client.tool("get_review_summaries", {});
        assertStringIncludes(read.text, "reviews/what_went_well: Every tool landed.");
        await client.tool("save_review_summary", { kind: "weekly", summary: "The connector grew up, mostly." });
        assertStringIncludes(
          (await client.tool("get_review_summaries", {})).text,
          "Every tool landed.",
          "saving the summary again keeps the reflections",
        );

        assert(!(await client.tool("finish_review", { kind: "weekly" })).isError);
        const [ritual] = await sql`
          select outcome from public.ritual_runs where owner_id = ${OWNER} and ritual = 'weekly_review'`;
        assertEquals(ritual.outcome, "done");
      });

      await t.step("the activity log reads back and a change is undone", async () => {
        const task = await client.tool("add_task", { title: "Rename me" });
        const taskId = /\(id ([0-9a-f-]{36})\)/.exec(task.text)![1];
        await client.tool("update_task", { id: taskId, title: "Renamed" });

        const log = await client.tool("get_activity", { limit: 20 });
        assert(!log.isError, log.text);
        assertStringIncludes(log.text, "by Claude");
        // The line for this task, rather than whatever happens to be newest.
        const mine = log.text.split("\n").find((line) => line.startsWith("- update") && line.includes(taskId));
        assert(mine !== undefined, `no line for the task that was renamed: ${log.text}`);
        const changeId = /\(change id ([0-9]+)/.exec(mine)![1];

        const undone = await client.tool("undo_change", { id: changeId });
        assert(!undone.isError, undone.text);
        const [back] = await sql`select title from public.tasks where id = ${taskId}`;
        assertEquals(back.title, "Rename me", "undo puts the row back the way the change found it");
        const twice = await client.tool("undo_change", { id: changeId });
        assert(twice.isError, twice.text);

        await sql`
          insert into public.activity_log (owner_id, entity, entity_id, action, actor, after)
          values (${OWNER}, 'tasks', ${taskId}, 'update', 'system', '{"title": "Swept up"}'::jsonb)`;
        assertStringIncludes(
          (await client.tool("get_activity", { limit: 3 })).text,
          "by GoalMaker",
          "a change GoalMaker made is not reported as the owner's",
        );
      });

      await t.step("the calendar and the settings read and change", async () => {
        await client.tool("add_task", { title: "Evening walk", day: "today", time: "19:00" });
        await client.tool("add_task", { title: "Untimed errand", day: "today" });
        await client.tool("add_task", { title: "Morning pages", day: "today", time: "07:00" });
        const week = await client.tool("get_calendar", { to: "today" });
        assert(!week.isError, week.text);
        const order = ["Morning pages", "Evening walk", "Untimed errand"].map((one) => week.text.indexOf(one));
        assert(
          order.every((at) => at >= 0) && order[0] < order[1] && order[1] < order[2],
          `a day runs earliest time first with untimed tasks after: ${week.text}`,
        );
        await client.tool("add_task", { title: "Weekly tidy", day: "today", repeat: "FREQ=WEEKLY" });
        const onlyToday = await client.tool("get_calendar", { to: "today" });
        assertStringIncludes(onlyToday.text, "Weekly tidy");
        assert(
          !onlyToday.text.includes("Weekly tidy · repeats"),
          `a repeat is never drawn on the day it is already planned for: ${onlyToday.text}`,
        );
        const ahead = await client.tool("get_calendar", { to: "2026-12-31" });
        assert(
          (ahead.text.match(/Weekly tidy · repeats/g) ?? []).length >= 2,
          `a repeating task is projected onto the days it comes round to: ${ahead.text}`,
        );
        const backwards = await client.tool("get_calendar", { from: "tomorrow", to: "today" });
        assert(backwards.isError, backwards.text);

        assertStringIncludes((await client.tool("get_settings")).text, "Europe/Prague");
        const moved = await client.tool("update_settings", { time_zone: "Asia/Tokyo", day_start_hour: 5 });
        assert(!moved.isError, moved.text);
        const [profile] = await sql`select time_zone, day_rollover_hour from public.profiles where id = ${OWNER}`;
        assertEquals(profile, { time_zone: "Asia/Tokyo", day_rollover_hour: 5 });
        const nowhere = await client.tool("update_settings", { time_zone: "Middle/Earth" });
        assert(nowhere.isError, nowhere.text);
        await client.tool("update_settings", { time_zone: "Europe/Prague", day_start_hour: 4 });
      });

      await t.step("the 121st call in a minute is refused", async () => {
        await sql`
          update public.connector_links set window_started_at = now(), window_calls = 120
          where owner_id = ${OWNER} and revoked_at is null`;
        const limited = await client.post("tools/list");
        assertEquals(limited.status, 429);
        assertEquals(limited.headers.get("retry-after"), "60");
        await limited.body?.cancel();
        await sql`update public.connector_links set window_calls = 0 where owner_id = ${OWNER} and revoked_at is null`;
      });

      await t.step("an unknown or revoked link is not found", async () => {
        const unknown = await new Client(`${api}/functions/v1/connector/${"x".repeat(43)}`).post("tools/list");
        assertEquals(unknown.status, 404);
        await unknown.body?.cancel();
        await sql`update public.connector_links set revoked_at = now() where owner_id = ${OWNER}`;
        const revoked = await client.post("tools/list");
        assertEquals(revoked.status, 404);
        await revoked.body?.cancel();
      });
    } finally {
      await sql`delete from auth.users where id in (${OWNER}, ${STRANGER})`;
      await sql.end();
    }
  },
});
