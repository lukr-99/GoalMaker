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
