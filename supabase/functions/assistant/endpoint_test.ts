// Drives the running assistant function on the local stack with scripted model turns (docs/assistant.md,
// "Tests"). Runs when GOALMAKER_ASSISTANT_TEST=1, after `npx supabase start` (and `functions serve` if the
// stack doesn't serve functions). No model is called: every request carries the FakeProvider's turns in
// the x-goalmaker-fake-turns header, which the function honours only on the local stack. It makes its
// own users in the database, signs their sessions with the local stack's JWT secret, and deletes them.
import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import { postgres } from "../_shared/deps.ts";
import { STOPPED } from "../_shared/assistant/toolLoop.ts";

const enabled = Deno.env.get("GOALMAKER_ASSISTANT_TEST") === "1";
const api = Deno.env.get("GOALMAKER_API_URL") ?? "http://127.0.0.1:55321";
const dbUrl = Deno.env.get("GOALMAKER_DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:55322/postgres";
// The local stack's fixed development secret (`npx supabase status`), never a real project's.
const jwtSecret = Deno.env.get("GOALMAKER_JWT_SECRET") ?? "super-secret-jwt-token-with-at-least-32-characters-long";

const OWNER = "c0ffee00-0000-4000-8000-0000000000a1";
const STRANGER = "c0ffee00-0000-4000-8000-0000000000a2";
const BUSY = "c0ffee00-0000-4000-8000-0000000000a3";
const SECRET_TASK = "c0ffee00-0000-4000-8000-0000000000af";

// deno-lint-ignore no-explicit-any
type Json = any;

function base64url(bytes: Uint8Array | string): string {
  const raw = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  return btoa(String.fromCharCode(...raw)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

/** A session token for a user, as Supabase Auth would sign it on the local stack. */
async function sessionFor(userId: string): Promise<string> {
  const header = base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64url(JSON.stringify({
    sub: userId,
    role: "authenticated",
    aud: "authenticated",
    iss: `${api}/auth/v1`,
    iat: now,
    exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(jwtSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${header}.${payload}`));
  return `${header}.${payload}.${base64url(new Uint8Array(signature))}`;
}

interface Answer {
  status: number;
  body: Json;
}

async function chat(token: string | null, body: unknown, turns?: unknown[]): Promise<Answer> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (token !== null) headers.authorization = `Bearer ${token}`;
  if (turns !== undefined) headers["x-goalmaker-fake-turns"] = JSON.stringify(turns);
  const response = await fetch(`${api}/functions/v1/assistant`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let parsed: Json = text;
  try {
    parsed = JSON.parse(text);
  } catch {
    // The gateway's own refusals are not always JSON.
  }
  return { status: response.status, body: parsed };
}

function ask(text: string) {
  return { messages: [{ role: "user", text }] };
}

Deno.test({
  name: "the assistant chats as the owner through GoalMaker's tools",
  ignore: !enabled,
  sanitizeResources: false,
  sanitizeOps: false,
  fn: async (t) => {
    const sql = postgres(dbUrl, { max: 1 });
    try {
      await sql`delete from auth.users where id in (${OWNER}, ${STRANGER}, ${BUSY})`;
      for (
        const [id, email] of [
          [OWNER, "assistant-owner@example.test"],
          [STRANGER, "assistant-stranger@example.test"],
          [BUSY, "assistant-busy@example.test"],
        ]
      ) {
        await sql`
          insert into auth.users (id, instance_id, aud, role, email)
          values (${id}, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', ${email})`;
      }
      await sql`update public.profiles set time_zone = 'Europe/Prague', day_rollover_hour = 4 where id = ${OWNER}`;
      await sql`
        insert into public.tasks (id, owner_id, title)
        values (${SECRET_TASK}, ${STRANGER}, 'The stranger''s secret task')`;
      const owner = await sessionFor(OWNER);

      await t.step("without a session the gateway refuses the call", async () => {
        const answer = await chat(null, ask("hi"), [{ text: "hello" }]);
        assertEquals(answer.status, 401, JSON.stringify(answer.body));
      });

      await t.step("a thread that isn't the call's shape is a bad request", async () => {
        for (
          const body of [
            {},
            { messages: [] },
            { messages: [{ role: "user", text: "hi" }, { role: "model", text: "Hello." }] },
            { messages: [{ role: "system", text: "hi" }] },
          ]
        ) {
          const answer = await chat(owner, body, [{ text: "never" }]);
          assertEquals(answer.status, 400, JSON.stringify(body));
          assertEquals(answer.body.error, "bad_request");
        }
      });

      await t.step("a script that isn't a list of turns is a bad request, and never reaches a model", async () => {
        // Every request here carries a script, so a model key on the local stack is never used.
        const answer = await chat(owner, ask("hi"), [{ neither: "text nor calls" }]);
        assertEquals(answer.status, 400, JSON.stringify(answer.body));
        assertEquals(answer.body.error, "bad_request");
      });

      let taskId = "";
      await t.step("a request that adds a task adds it as the owner, through the chat", async () => {
        const answer = await chat(owner, ask("add call the bank today at 9"), [
          {
            calls: [{
              name: "add_task",
              args: { title: "Call the bank", day: "today", time: "9:00", made_by: "claude" },
            }],
          },
          { text: "{{results}}" },
        ]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertStringIncludes(answer.body.text, "Added:");
        assertStringIncludes(answer.body.text, "Call the bank");
        taskId = /\(id ([0-9a-f-]{36})\)/.exec(answer.body.text)![1];
        const [row] = await sql`
          select owner_id::text, planned_time::text, made_by from public.tasks where id = ${taskId}`;
        assertEquals(row, { owner_id: OWNER, planned_time: "09:00:00", made_by: "owner" });
        const [log] = await sql`
          select actor, via from public.activity_log where entity = 'tasks' and entity_id = ${taskId} and action = 'create'`;
        assertEquals(log, { actor: "owner", via: "chat" });
      });

      await t.step("a habit, a goal and a want are added from the owner's short lines", async () => {
        const answer = await chat(owner, ask("add swim 2 times a week, read 3 books this month, and a kindle"), [
          {
            calls: [
              { name: "add_habit", args: { line: "Swim 2 times a week 40 min" } },
              { name: "add_goal", args: { line: "Read 3 books this month" } },
              // The script travels in a header, which holds only Latin-1, so CZK rather than Kč.
              { name: "add_want", args: { line: "Kindle 3290 CZK because I read on the train" } },
            ],
          },
          { text: "{{results}}" },
        ]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertStringIncludes(answer.body.text, "Swim · 2 times a week · left · 0 of 2 this week");
        assertStringIncludes(answer.body.text, "Read 3 books · month of");
        assertStringIncludes(answer.body.text, "Kindle · 3290 CZK · cooling");
        const [habit] = await sql`
          select cadence, times, measure, target, unit from public.habits where owner_id = ${OWNER} and name = 'Swim'`;
        assertEquals(habit, { cadence: "per_week", times: 2, measure: "amount", target: 40, unit: "min" });
        const [goal] = await sql`
          select horizon, target, unit from public.goals where owner_id = ${OWNER} and title = 'Read 3 books'`;
        assertEquals(goal, { horizon: "month", target: 3, unit: "books" });
        const [want] = await sql`
          select price, currency, reason from public.wants where owner_id = ${OWNER} and title = 'Kindle'`;
        assertEquals(want, { price: 3290, currency: "CZK", reason: "I read on the train" });
      });

      await t.step("a request that reads Today answers from the owner's list", async () => {
        const answer = await chat(owner, {
          messages: [
            { role: "user", text: "add call the bank today at 9" },
            { role: "model", text: "Added it." },
            { role: "user", text: "what's on today?" },
          ],
        }, [{ calls: [{ name: "get_today", args: {} }] }, { text: "Today: {{results}}" }]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertStringIncludes(answer.body.text, "Call the bank · 09:00");
        assert(!answer.body.text.includes("stranger"), answer.body.text);
      });

      await t.step("a request that needs two tool rounds carries the first round's work into the second", async () => {
        const answer = await chat(owner, ask("put buy milk on today and show me today"), [
          { calls: [{ name: "add_task", args: { title: "Buy milk", day: "today" } }] },
          { calls: [{ name: "get_today", args: {} }] },
          { text: "{{results}}" },
        ]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertStringIncludes(answer.body.text, "Buy milk");
        assertStringIncludes(answer.body.text, "Call the bank");
      });

      await t.step("the activity log says a change came through the chat", async () => {
        const answer = await chat(owner, ask("what changed?"), [
          { calls: [{ name: "get_activity", args: { limit: 5 } }] },
          { text: "{{results}}" },
        ]);
        assertStringIncludes(answer.body.text, 'create tasks "Buy milk", by the owner through the chat');
      });

      await t.step("a delete is not offered, and asking for one deletes nothing", async () => {
        const offered = await chat(owner, ask("what can you do?"), [{ text: "{{tools}}" }]);
        const names = offered.body.text.split(",");
        assert(names.includes("add_task") && names.includes("get_today"), offered.body.text);
        assert(!names.some((name: string) => /^(delete|remove)_|^undo_change$/.test(name)), offered.body.text);

        const answer = await chat(owner, ask("delete the bank task"), [
          { calls: [{ name: "delete_task", args: { id: taskId, confirmed: true } }] },
          { text: "{{results}}" },
        ]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertStringIncludes(answer.body.text, "There is no tool called delete_task");
        const [row] = await sql`select deleted_at from public.tasks where id = ${taskId}`;
        assertEquals(row.deleted_at, null);
      });

      await t.step("arguments that don't fit a tool's shape come back to the model as an error", async () => {
        const answer = await chat(owner, ask("add something"), [
          { calls: [{ name: "add_task", args: { title: 42 } }] },
          { text: "{{results}}" },
        ]);
        assertStringIncludes(answer.body.text, "Those arguments don't fit add_task");
      });

      await t.step("the rounds run out after eight and the chat says so", async () => {
        const turns = Array.from({ length: 9 }, (_, n) => ({
          calls: [{ name: "add_task", args: { title: `Round ${n + 1}` } }],
        }));
        const answer = await chat(owner, ask("keep going"), turns);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assertEquals(answer.body.text, STOPPED);
        const [row] = await sql`
          select count(*)::int as made from public.tasks where owner_id = ${OWNER} and title like 'Round %'`;
        assertEquals(row.made, 8);
      });

      await t.step("another owner's rows are never reachable", async () => {
        const answer = await chat(owner, ask("show me the secret task"), [
          {
            calls: [
              { name: "get_task", args: { id: SECRET_TASK } },
              { name: "search_tasks", args: { query: "secret" } },
              { name: "update_task", args: { id: SECRET_TASK, title: "Mine now" } },
              { name: "complete_task", args: { id: SECRET_TASK } },
            ],
          },
          { text: "{{results}}" },
        ]);
        assertEquals(answer.status, 200, JSON.stringify(answer.body));
        assert(!answer.body.text.includes("stranger"), answer.body.text);
        assertStringIncludes(answer.body.text, 'Nothing matches "secret".');
        const [row] = await sql`select title, status from public.tasks where id = ${SECRET_TASK}`;
        assertEquals(row, { title: "The stranger's secret task", status: "open" });
      });

      await t.step("the limits: 30 requests a minute, then a daily cap", async () => {
        const busy = await sessionFor(BUSY);
        for (let n = 1; n <= 30; n++) {
          const answer = await chat(busy, ask(`hello ${n}`), [{ text: "Hi." }]);
          assertEquals(answer.status, 200, `request ${n}: ${JSON.stringify(answer.body)}`);
        }
        const refused = await chat(busy, ask("one more"), [{ text: "Hi." }]);
        assertEquals(refused.status, 429);
        assertEquals(refused.body.error, "rate_limited");

        // Another owner's minute is their own.
        assertEquals((await chat(owner, ask("hi"), [{ text: "Hi." }])).status, 200);

        await sql`
          update public.assistant_usage set minute_started_at = now() - interval '2 minutes', day_calls = 200
          where owner_id = ${BUSY}`;
        const capped = await chat(busy, ask("next minute"), [{ text: "Hi." }]);
        assertEquals(capped.status, 429);
        assertEquals(capped.body.error, "rate_limited");
        assertStringIncludes(capped.body.message, "today");
      });
    } finally {
      await sql`delete from auth.users where id in (${OWNER}, ${STRANGER}, ${BUSY})`;
      await sql.end();
    }
  },
});
