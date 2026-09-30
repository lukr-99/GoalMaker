// Drives the running connector on the local stack through its MCP endpoint (spec, Testing: connector).
// Runs when GOALMAKER_CONNECTOR_TEST=1, after `npx supabase start` (and `functions serve` if the stack
// doesn't serve functions). It makes its own user and link in the database and deletes them after.
import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import { postgres } from "../_shared/deps.ts";
import { localNow } from "../_shared/rules/day.ts";
import { defaultPeriod } from "../_shared/rules/digest.ts";
import { planningDay } from "../_shared/rules/planningDay.ts";
import { reviewId } from "../_shared/rules/reviews.ts";
import { cooldownsId } from "../_shared/rules/wants.ts";

const enabled = Deno.env.get("GOALMAKER_CONNECTOR_TEST") === "1";
const api = Deno.env.get("GOALMAKER_API_URL") ?? "http://127.0.0.1:55321";
const dbUrl = Deno.env.get("GOALMAKER_DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:55322/postgres";

const OWNER = "c0ffee00-0000-4000-8000-000000000001";
const STRANGER = "c0ffee00-0000-4000-8000-000000000002";
const REVIEWER = "c0ffee00-0000-4000-8000-000000000003";

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

        const [maker] = await sql`select made_by from public.tasks where id = ${itemId}`;
        assertEquals(maker.made_by, "claude", "an item Claude adds on its own is Claude's");

        const board = await client.tool("get_project_board", { project: "GoalMaker" });
        assertStringIncludes(board.text, "Backlog (1):");
        assertStringIncludes(board.text, "[ ] Share to GoalMaker · idea · high · M5 · by Claude");
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

        const asked = await client.tool("add_project_item", {
          project: "GoalMaker",
          title: "Dark mode for the widget",
          type: "idea",
          made_by: "owner",
        });
        assert(!asked.isError, asked.text);
        const askedId = /\(id ([0-9a-f-]{36})\)/.exec(asked.text)![1];
        const [askedRow] = await sql`select made_by from public.tasks where id = ${askedId}`;
        assertEquals(askedRow.made_by, "owner", "an item the owner asked Claude for is the owner's");
        const claudes = await client.tool("get_project_board", { project: "GoalMaker", made_by: "claude" });
        assertStringIncludes(claudes.text, "Only the items Claude made.");
        assertStringIncludes(claudes.text, "The ring flickers");
        assert(!claudes.text.includes("Dark mode for the widget"), claudes.text);
        const owners = await client.tool("get_project_board", { project: "GoalMaker", made_by: "owner" });
        assertStringIncludes(owners.text, "Only the items the owner made.");
        assertStringIncludes(owners.text, "[ ] Dark mode for the widget · idea (id");
        assert(!owners.text.includes("The ring flickers"), owners.text);

        const daily = await client.tool("add_task", {
          title: "Water the plants",
          day: "today",
          repeat: "FREQ=DAILY",
          made_by: "owner",
        });
        const dailyId = /\(id ([0-9a-f-]{36})\)/.exec(daily.text)![1];
        await client.tool("complete_task", { id: dailyId });
        const [next] = await sql`select made_by from public.tasks where series_id = ${dailyId} and id <> ${dailyId}`;
        assertEquals(next.made_by, "owner", "a repeating task's next occurrence keeps who made it");

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
          !onlyToday.text.includes("would come round"),
          `a repeat is never drawn on the day it is already planned for: ${onlyToday.text}`,
        );
        const ahead = await client.tool("get_calendar", { to: "2026-12-31" });
        assert(
          (ahead.text.match(/Weekly tidy .* · would come round/g) ?? []).length >= 2,
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

      await t.step("wants are added, price checked, decided and undone through the connector", async () => {
        // This step makes more calls than the minute's budget has left after the ones before it.
        await sql`update public.connector_links set window_calls = 0 where owner_id = ${OWNER} and revoked_at is null`;
        const tools = (await client.call("tools/list")).tools.map((tool: Json) => tool.name);
        for (const name of ["get_wants", "add_want", "update_want", "decide_want", "record_price_check"]) {
          assert(tools.includes(name), `${name} in ${tools}`);
        }
        const wantOf = (text: string) => /\(want id ([0-9a-f-]{36})\)/.exec(text)![1];
        const rowOf = async (id: string) =>
          (await sql`
            select cooldown_days, (cools_until - added_on) as waits, currency, made_by, decision, decision_note,
                   checked_price, checked_note
            from public.wants where id = ${id}`)[0];

        // The defaults of wants.json: 7 days under 1,000, 30 under 10,000, and 30 with no price.
        const cheap = await client.tool("add_want", {
          title: "Headphone pads",
          reason: "The old ones cracked",
          price: 500,
        });
        assert(!cheap.isError, cheap.text);
        assertStringIncludes(cheap.text, "by Claude");
        const cheapId = wantOf(cheap.text);
        assertEquals(await rowOf(cheapId), {
          cooldown_days: 7,
          waits: 7,
          currency: "CZK",
          made_by: "claude",
          decision: null,
          decision_note: "",
          checked_price: null,
          checked_note: "",
        });
        const edge = await client.tool("add_want", { title: "Kettle", reason: "Ours leaks", price: 1000 });
        assertEquals((await rowOf(wantOf(edge.text))).cooldown_days, 30, "exactly 1,000 is no longer small");
        const unpriced = await client.tool("add_want", { title: "Tent", reason: "Summer trips" });
        assertEquals((await rowOf(wantOf(unpriced.text))).cooldown_days, 30);
        const foreign = await client.tool("add_want", {
          title: "Book",
          reason: "Recommended",
          price: 20,
          currency: "eur",
        });
        assertEquals((await rowOf(wantOf(foreign.text))).currency, "EUR");
        assertEquals((await rowOf(wantOf(foreign.text))).cooldown_days, 30, "another currency cools like no price");
        const picked = await client.tool("add_want", {
          title: "Lamp",
          reason: "Dark desk",
          price: 50000,
          cooldown_days: 2,
        });
        assertEquals((await rowOf(wantOf(picked.text))).cooldown_days, 2);
        const [log] = await sql`
          select actor from public.activity_log where entity = 'wants' and entity_id = ${cheapId} and action = 'create'`;
        assertEquals(log.actor, "claude");

        // The owner's own thresholds are the ones the apps use too.
        await sql`
          insert into public.want_cooldowns (id, owner_id, small_days) values (${await cooldownsId(
          OWNER,
        )}, ${OWNER}, 3)`;
        const mine = await client.tool("add_want", { title: "Socks", reason: "Holes", price: 200 });
        assertEquals((await rowOf(wantOf(mine.text))).cooldown_days, 3);

        const noReason = await client.tool("add_want", { title: "Anything", reason: "  " });
        assert(noReason.isError, noReason.text);
        const badCurrency = await client.tool("add_want", { title: "Anything", reason: "Why not", currency: "euro" });
        assert(badCurrency.isError, badCurrency.text);

        const edited = await client.tool("update_want", { id: cheapId, title: "Headphone ear pads", area: "Home" });
        assert(!edited.isError, edited.text);
        assertStringIncludes(edited.text, "@Home");
        assertEquals((await rowOf(cheapId)).cooldown_days, 7, "an edit never moves the cooldown");

        const checked = await client.tool("record_price_check", {
          id: cheapId,
          price: 390,
          where: "Alza, https://www.alza.cz/pads",
          alternatives: "Generic pads 150 CZK at Mall",
        });
        assert(!checked.isError, checked.text);
        assertStringIncludes(checked.text, "Last checked: 390 CZK");
        const afterCheck = await rowOf(cheapId);
        assertEquals(afterCheck.checked_price, 390);
        assertStringIncludes(afterCheck.checked_note, "Alternatives: Generic pads 150 CZK at Mall");

        const cooling = await client.tool("get_wants");
        assertStringIncludes(cooling.text, "Cooling:");
        assertStringIncludes(cooling.text, "Why: The old ones cracked");
        const lampReady = await client.tool("get_wants", { state: "ready" });
        assertEquals(lampReady.text, "No ready wants.");

        const bought = await client.tool("decide_want", { id: cheapId, decision: "bought", note: "The cheaper pads" });
        assert(!bought.isError, bought.text);
        assertEquals((await rowOf(cheapId)).decision, "bought");
        const decided = await client.tool("get_wants", { state: "decided" });
        assertStringIncludes(decided.text, "Headphone ear pads · 500 CZK · bought");
        assertStringIncludes(decided.text, "Note: The cheaper pads");

        const history = await client.tool("get_activity", { limit: 10 });
        const decision = history.text.split("\n").find((line) => line.startsWith("- update") && line.includes(cheapId));
        assert(decision !== undefined, history.text);
        const undone = await client.tool("undo_change", { id: /\(change id ([0-9]+)/.exec(decision)![1] });
        assert(!undone.isError, undone.text);
        assertEquals((await rowOf(cheapId)).decision, null, "undo takes the decision back");

        const dropped = await client.tool("decide_want", { id: cheapId, decision: "dropped" });
        assert(!dropped.isError, dropped.text);
        const reopened = await client.tool("decide_want", { id: cheapId, decision: "reopen" });
        assert(!reopened.isError, reopened.text);
        assertEquals((await rowOf(cheapId)).decision, null);
        const again = await client.tool("decide_want", { id: cheapId, decision: "reopen" });
        assert(again.isError, again.text);

        const stranger = await client.tool("decide_want", {
          id: "c0ffee00-0000-4000-8000-0000000000aa",
          decision: "bought",
        });
        assert(stranger.isError, stranger.text);
      });

      await t.step("done items leave the board by age or by hand, and come back when reopened", async () => {
        await sql`update public.connector_links set window_calls = 0 where owner_id = ${OWNER} and revoked_at is null`;
        const made = await client.tool("create_project", { name: "Archive test" });
        assert(!made.isError, made.text);
        const projectId = /\(project id ([0-9a-f-]{36})\)/.exec(made.text)![1];
        const item = async (title: string) =>
          /\(id ([0-9a-f-]{36})\)/.exec(
            (await client.tool("add_project_item", { project: projectId, title })).text,
          )![1];
        const fresh = await item("Done today");
        const old = await item("Done long ago");
        const open = await item("Still to do");
        await client.tool("move_project_item", { id: fresh, column: "done" });
        await client.tool("move_project_item", { id: old, column: "done" });
        await sql`update public.tasks set completed_at = now() - interval '20 days' where id = ${old}`;

        let board = await client.tool("get_project_board", { project: projectId });
        assertStringIncludes(board.text, "Done today");
        assertStringIncludes(board.text, "Still to do");
        assert(!board.text.includes("Done long ago"), `14 days by default: ${board.text}`);
        assertStringIncludes(board.text, "1 done item(s) are off the board");

        const never = await client.tool("update_project", { project: projectId, archive_after_days: null });
        assert(!never.isError, never.text);
        board = await client.tool("get_project_board", { project: projectId });
        assertStringIncludes(board.text, "Done long ago");

        const archived = await client.tool("update_project_item", { id: fresh, archived: true });
        assert(!archived.isError, archived.text);
        board = await client.tool("get_project_board", { project: projectId });
        assert(!board.text.includes("Done today"), board.text);
        assertStringIncludes(board.text, "(archived by hand)");
        const notDone = await client.tool("update_project_item", { id: open, archived: true });
        assert(notDone.isError, notDone.text);

        await client.tool("move_project_item", { id: fresh, column: "todo" });
        const [row] = await sql`select board_archived_at from public.tasks where id = ${fresh}`;
        assertEquals(row.board_archived_at, null, "reopening brings it back");
        board = await client.tool("get_project_board", { project: projectId });
        assertStringIncludes(board.text, "Done today");
        const tooMany = await client.tool("update_project", { project: projectId, archive_after_days: 0 });
        assert(tooMany.isError, tooMany.text);
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

/** A fixed August 2026 for the review prompts and the digest: every day in it is long past. */
async function seedAugust(sql: postgres.Sql, owner: string) {
  const id = (n: number) => `d1e57000-0000-4000-8000-${String(n).padStart(12, "0")}`;
  await sql`update public.profiles set time_zone = 'Europe/Prague', day_rollover_hour = 4 where id = ${owner}`;
  await sql`insert into public.areas (id, owner_id, name, color) values (${id(1)}, ${owner}, 'Health', 'violet')`;
  await sql`insert into public.projects (id, owner_id, name) values (${id(2)}, ${owner}, 'Garden')`;
  const task = async (
    n: number,
    title: string,
    fields: {
      status?: string;
      day?: string;
      done?: string;
      area?: boolean;
      project?: boolean;
      moves?: number;
      deadline?: string;
      created: string;
    },
  ) => {
    await sql`
      insert into public.tasks (id, owner_id, title, status, planned_date, completed_at, area_id, project_id,
                                board_column, moved_count, deadline, created_at)
      values (${id(n)}, ${owner}, ${title}, ${fields.status ?? "open"}, ${fields.day ?? null}, ${fields.done ?? null},
              ${fields.area ? id(1) : null}, ${fields.project ? id(2) : null},
              ${fields.project ? (fields.status === "done" ? "done" : "todo") : null}, ${fields.moves ?? 0},
              ${fields.deadline ?? null}, ${fields.created})`;
  };
  await task(10, "Last week's errand", {
    status: "done",
    day: "2026-08-11",
    done: "2026-08-11T12:00:00Z",
    created: "2026-08-01T08:00:00Z",
  });
  await task(11, "Book the dentist", {
    status: "done",
    day: "2026-08-18",
    done: "2026-08-18T09:00:00Z",
    area: true,
    created: "2026-08-02T08:00:00Z",
  });
  await task(12, "Prune the roses", {
    status: "done",
    day: "2026-08-19",
    done: "2026-08-19T16:00:00Z",
    project: true,
    created: "2026-08-03T08:00:00Z",
  });
  await task(13, "Call the bank", { day: "2026-08-20", moves: 4, created: "2026-08-04T08:00:00Z" });
  await task(14, "An old errand", { day: "2026-08-12", created: "2026-08-05T08:00:00Z" });
  await task(15, "Plan the trip", { day: "2026-08-25", deadline: "2026-08-28", created: "2026-08-06T08:00:00Z" });
  await task(16, "Let it go", { status: "dropped", day: "2026-08-19", created: "2026-08-07T08:00:00Z" });
  await task(17, "Renew the passport", { deadline: "2026-08-27", created: "2026-08-08T08:00:00Z" });

  await sql`
    insert into public.goals (id, owner_id, title, horizon, period_start, progress_mode, target, unit)
    values (${id(20)}, ${owner}, 'Run 20 km', 'week', '2026-08-17', 'number', 20, 'km'),
           (${id(21)}, ${owner}, 'Read a book', 'week', '2026-08-24', 'done', null, null),
           (${id(22)}, ${owner}, 'Tidy the garden', 'month', '2026-08-01', 'done', null, null)`;
  await sql`
    insert into public.goal_entries (id, owner_id, goal_id, day, amount)
    values (${id(23)}, ${owner}, ${id(20)}, '2026-08-18', 5)`;

  await sql`
    insert into public.habits (id, owner_id, name, cadence, measure, starts_on)
    values (${id(30)}, ${owner}, 'Read', 'daily', 'check', '2026-08-01'),
           (${id(31)}, ${owner}, 'Stretch', 'daily', 'check', '2026-08-17')`;
  await sql`
    insert into public.habit_checkins (id, owner_id, habit_id, day, value, skipped)
    values (${id(32)}, ${owner}, ${id(30)}, '2026-08-17', 1, false),
           (${id(33)}, ${owner}, ${id(30)}, '2026-08-18', 1, false),
           (${id(34)}, ${owner}, ${id(31)}, '2026-08-17', 1, false),
           (${id(35)}, ${owner}, ${id(31)}, '2026-08-18', 0, true)`;

  await sql`
    insert into public.wants (id, owner_id, title, reason, price, cooldown_days, added_on, cools_until, decision,
                              decided_at, made_by)
    values (${id(40)}, ${owner}, 'Headphones', 'The old ones broke', 2500, 30, '2026-07-19', '2026-08-18', null, null,
            'owner'),
           (${id(41)}, ${owner}, 'Kettle', 'Ours leaks', 800, 7, '2026-08-05', '2026-08-12', 'bought',
            '2026-08-20T10:00:00Z', 'owner'),
           (${id(42)}, ${owner}, 'Tent', 'Summer trips', null, 30, '2026-07-26', '2026-08-25', null, null, 'claude')`;

  // A fortnight of Tally: a PC coding in the Garden project and a phone watching video, an own category,
  // and a rule whose pattern names an app, which must never come back through the connector.
  await sql`
    insert into public.tally_categories (id, owner_id, name, color) values (${id(50)}, ${owner}, 'Music', 'lime')`;
  await sql`
    insert into public.tally_rules (id, owner_id, match, pattern, platform, category)
    values (${id(51)}, ${owner}, 'app', 'SecretGame.exe', 'windows', 'games')`;
  for (let offset = 0; offset < 14; offset++) {
    const day = new Date(Date.UTC(2026, 7, 10 + offset)).toISOString().slice(0, 10);
    await sql`
      insert into public.tally_days (id, owner_id, day, device, device_kind, category, project_id, minutes)
      values (${crypto.randomUUID()}, ${owner}, ${day}, 'd1e57000-0000-4000-8000-00000000aaaa', 'pc', 'coding',
              ${id(2)}, 60),
             (${crypto.randomUUID()}, ${owner}, ${day}, 'd1e57000-0000-4000-8000-00000000bbbb', 'phone', 'video',
              null, 30)`;
  }
  await sql`
    insert into public.tally_days (id, owner_id, day, device, device_kind, category, minutes)
    values (${crypto.randomUUID()}, ${owner}, '2026-08-20', 'd1e57000-0000-4000-8000-00000000aaaa', 'pc', ${
    id(50)
  }, 45)`;

  await sql`
    insert into public.reviews (id, owner_id, kind, period_start, summary, mood, energy, reflections)
    values (${await reviewId(owner, "weekly", "2026-08-10")}, ${owner}, 'weekly', '2026-08-10',
            'Last week was calm.', null, null, '[]'::jsonb),
           (${await reviewId(owner, "weekly", "2026-08-17")}, ${owner}, 'weekly', '2026-08-17', '', 4, 3,
            '[{"prompt": "wins/proud", "answer": "The dentist, finally."}]'::jsonb)`;
}

Deno.test({
  name: "the review prompts and the digest read a fixed August",
  ignore: !enabled,
  sanitizeResources: false,
  sanitizeOps: false,
  fn: async (t) => {
    const sql = postgres(dbUrl, { max: 1 });
    try {
      await sql`delete from auth.users where id = ${REVIEWER}`;
      await sql`
        insert into auth.users (id, instance_id, aud, role, email)
        values (${REVIEWER}, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
                'connector-reviewer@example.test')`;
      await seedAugust(sql, REVIEWER);
      const secret = await sql.begin(async (db) => {
        await db`select set_config('request.jwt.claims', ${
          JSON.stringify({ sub: REVIEWER, role: "authenticated" })
        }, true)`;
        await db`set local role authenticated`;
        return (await db`select public.create_connector_link() as secret`)[0].secret as string;
      });
      const client = new Client(`${api}/functions/v1/connector/${secret}`);
      const promptText = async (name: string, args: Record<string, string>) =>
        (await client.call("prompts/get", { name, arguments: args })).messages[0].content.text as string;

      await t.step("the review prompts read the same as their snapshots", async () => {
        const cases: [string, Record<string, string>][] = [
          ["weekly_review", { week_start: "2026-08-17" }],
          ["monthly_review", { month: "2026-08" }],
        ];
        for (const [name, args] of cases) {
          const text = await promptText(name, args);
          const file = new URL(`./snapshots/${name}.txt`, import.meta.url);
          if (Deno.env.get("GOALMAKER_UPDATE_SNAPSHOTS") === "1") await Deno.writeTextFile(file, text);
          assertEquals(text, await Deno.readTextFile(file), `${name} changed; the snapshot is the text before M8-07`);
        }
      });

      const digestOf = async (args: Record<string, string>) => {
        const result = await client.tool("get_review_digest", args);
        assert(!result.isError, result.text);
        return JSON.parse(result.text);
      };
      const id = (n: number) => `d1e57000-0000-4000-8000-${String(n).padStart(12, "0")}`;
      const ids = (items: { id: string }[]) => items.map((item) => item.id);

      await t.step("a seeded week comes back whole in one call", async () => {
        const week = await digestOf({ kind: "weekly", period: "2026-08-19" });
        assertEquals(week.period, { kind: "weekly", start: "2026-08-17", end: "2026-08-23" });
        assertEquals(week.done, [
          { day: "2026-08-18", tasks: [{ id: id(11), title: "Book the dentist", area: "Health", project: null }] },
          { day: "2026-08-19", tasks: [{ id: id(12), title: "Prune the roses", area: null, project: "Garden" }] },
        ]);
        assertEquals(ids(week.open.left), [id(13)]);
        assertEquals(week.open.left[0].moves, 4);
        assertEquals(ids(week.open.overdue).sort(), [id(13), id(14), id(15)].sort());
        assertEquals(ids(week.open.slipping), [id(13)]);
        assertEquals(week.goals, [{
          id: id(20),
          title: "Run 20 km",
          status: "open",
          counts: "number",
          value: 5,
          target: 20,
          unit: "km",
          progress: 25,
          expected_by_now: 100,
          reached: false,
        }]);
        assertEquals(
          week.habits.map((habit: Json) => [habit.name, habit.met, habit.missed, habit.skipped, habit.periods]),
          [["Read", 2, 5, 0, 7], ["Stretch", 1, 5, 1, 7]],
        );
        assertEquals(week.projects, [{ project: "Garden", done: [{ id: id(12), title: "Prune the roses" }] }]);
        assertEquals(week.triggers.map((one: Json) => one.trigger), [
          "goal_behind",
          "habit_missed",
          "task_slipping",
          "busy_period",
        ]);
        assertEquals(week.review, {
          mood: 4,
          energy: 3,
          reflections: [{ prompt: "wins/proud", answer: "The dentist, finally." }],
          has_letter: false,
        });
        assertEquals(week.last_letter, { period_start: "2026-08-10", letter: "Last week was calm." });
        assertEquals([week.next.start, week.next.end], ["2026-08-24", "2026-08-30"]);
        assertEquals(ids(week.next.tasks), [id(15)]);
        assertEquals(ids(week.next.deadlines), [id(17), id(15)]);
        assertEquals(ids(week.next.goals), [id(21)]);
        assertEquals(ids(week.wants.became_ready), [id(40)]);
        assertEquals(week.wants.decided.map((want: Json) => [want.title, want.decision, want.decided_on]), [
          ["Kettle", "bought", "2026-08-20"],
        ]);
        assertEquals(ids(week.wants.ready_next), [id(42)]);
      });

      await t.step("without a period it is the one holding yesterday", async () => {
        const today = planningDay(localNow("Europe/Prague", new Date()), 4);
        for (const kind of ["weekly", "monthly"] as const) {
          const found = await digestOf({ kind });
          const expected = defaultPeriod(kind, today);
          assertEquals(found.period, { kind, start: expected.start, end: expected.end });
        }
        assertEquals((await digestOf({})).period.kind, "weekly");
        const nonsense = await client.tool("get_review_digest", { period: "last week" });
        assert(nonsense.isError, nonsense.text);
      });

      await t.step("time is grouped by category, project and device, and never names an app", async () => {
        const fortnight = { from: "2026-08-10", to: "2026-08-23" };
        const byCategory = await client.tool("get_time_tally", fortnight);
        assert(!byCategory.isError, byCategory.text);
        assertStringIncludes(byCategory.text, "by category: 21 h 45 min over 14 days");
        assertStringIncludes(byCategory.text, "- Coding · 14 h · 64%");
        assertStringIncludes(byCategory.text, "- Video · 7 h · 32%");
        assertStringIncludes(byCategory.text, "- Music · 45 min · 3%");
        const byProject = await client.tool("get_time_tally", { ...fortnight, by: "project" });
        assertStringIncludes(byProject.text, "- Garden · 14 h");
        assertStringIncludes(byProject.text, "- No project · 7 h 45 min");
        const byDevice = await client.tool("get_time_tally", { ...fortnight, by: "device" });
        assertStringIncludes(byDevice.text, "- PC · 14 h 45 min");
        assertStringIncludes(byDevice.text, "- Phone · 7 h");
        const none = await client.tool("get_time_tally", { from: "2026-07-01", to: "2026-07-07" });
        assertStringIncludes(none.text, "No Tally time");
        const backwards = await client.tool("get_time_tally", { from: "2026-08-23", to: "2026-08-10" });
        assert(backwards.isError, backwards.text);

        const week = await digestOf({ kind: "weekly", period: "2026-08-19" });
        assertEquals(week.tally, {
          minutes: 675,
          by_category: [{ category: "Coding", minutes: 420 }, { category: "Video", minutes: 210 }, {
            category: "Music",
            minutes: 45,
          }],
          by_project: [{ project: "Garden", minutes: 420 }, { project: "No project", minutes: 255 }],
          by_device: [{ device: "PC", minutes: 465 }, { device: "Phone", minutes: 210 }],
        });
        for (const answer of [byCategory.text, byProject.text, byDevice.text, JSON.stringify(week)]) {
          assert(!answer.includes("SecretGame"), `an app's name came back: ${answer}`);
        }
      });

      await t.step("a monthly digest reads the whole month", async () => {
        const month = await digestOf({ kind: "monthly", period: "2026-08-31" });
        assertEquals(month.period, { kind: "monthly", start: "2026-08-01", end: "2026-08-31" });
        assertEquals(month.done.flatMap((day: Json) => ids(day.tasks)), [id(10), id(11), id(12)]);
        assertEquals(month.goals.map((goal: Json) => goal.title), ["Tidy the garden"]);
        assertEquals([month.next.start, month.next.end], ["2026-09-01", "2026-09-30"]);
        assertEquals(month.last_letter, null);
      });
    } finally {
      await sql`delete from auth.users where id = ${REVIEWER}`;
      await sql.end();
    }
  },
});
