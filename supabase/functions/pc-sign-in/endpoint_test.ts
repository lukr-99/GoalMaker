// Drives the running pc-sign-in function on the local stack the way the PC and the phone do (ADR 0020).
// Runs when GOALMAKER_PC_SIGN_IN_TEST=1, after `npx supabase start` (and `functions serve` if the stack
// doesn't serve functions). It makes its own owner, approves as that owner through PostgREST with a
// session signed by the local stack's JWT secret, signs in with the code it picks up, and deletes the
// owner.
import { assert, assertEquals } from "jsr:@std/assert@1.0.13";
import { postgres } from "../_shared/deps.ts";

const enabled = Deno.env.get("GOALMAKER_PC_SIGN_IN_TEST") === "1";
const api = Deno.env.get("GOALMAKER_API_URL") ?? "http://127.0.0.1:55321";
const dbUrl = Deno.env.get("GOALMAKER_DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:55322/postgres";
// The local stack's fixed development secret, service role key and anon key (`npx supabase status`), never a real project's.
const jwtSecret = Deno.env.get("GOALMAKER_JWT_SECRET") ?? "super-secret-jwt-token-with-at-least-32-characters-long";
const serviceKey = Deno.env.get("GOALMAKER_SERVICE_ROLE_KEY") ??
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU";
const anonKey = Deno.env.get("GOALMAKER_ANON_KEY") ??
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0";

const OWNER = "c0ffee00-0000-4000-8000-0000000000b1";
const EMAIL = "pc-sign-in-owner@example.test";

// deno-lint-ignore no-explicit-any
type Json = any;

function base64url(bytes: Uint8Array | string): string {
  const raw = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  return btoa(String.fromCharCode(...raw)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function sign(claims: Record<string, unknown>): Promise<string> {
  const header = base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64url(JSON.stringify({ iss: `${api}/auth/v1`, iat: now, exp: now + 3600, ...claims }));
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

// Every run is a caller of its own, so the per-caller limits start fresh. The local stack has no
// Cloudflare in front, so the header passes through as the hosted one would set it.
const caller = `192.0.2.${Math.floor(Math.random() * 250) + 1}-${crypto.randomUUID()}`;

async function post(path: string, body: unknown, token: string): Promise<{ status: number; body: Json }> {
  const response = await fetch(`${api}${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      apikey: anonKey,
      authorization: `Bearer ${token}`,
      "cf-connecting-ip": caller,
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  try {
    return { status: response.status, body: JSON.parse(text) };
  } catch {
    return { status: response.status, body: text };
  }
}

Deno.test({
  name: "a PC signs in with a code the phone approved",
  ignore: !enabled,
  sanitizeResources: false,
  sanitizeOps: false,
  fn: async (t) => {
    const sql = postgres(dbUrl, { max: 1 });
    const anon = anonKey;
    const owner = await sign({ sub: OWNER, role: "authenticated", aud: "authenticated" });
    try {
      await sql`delete from auth.users where id = ${OWNER}`;
      // Made through Supabase Auth, so the user has every column Auth reads when it makes the code.
      const made = await fetch(`${api}/auth/v1/admin/users`, {
        method: "POST",
        headers: { "content-type": "application/json", apikey: serviceKey, authorization: `Bearer ${serviceKey}` },
        body: JSON.stringify({ id: OWNER, email: EMAIL, email_confirm: true }),
      });
      assertEquals(made.status, 200, await made.text());

      await t.step("a call that isn't one of the two is a bad request", async () => {
        for (const body of [{}, { action: "start" }, { action: "pick_up", id: "x", pickup: "y" }]) {
          const answer = await post("/functions/v1/pc-sign-in", body, anon);
          assertEquals(answer.status, 400, JSON.stringify(body));
        }
      });

      const started = await post("/functions/v1/pc-sign-in", { action: "start", device: "TEST-PC" }, anon);
      assertEquals(started.status, 200, JSON.stringify(started.body));
      const { id, scan, pickup, match } = started.body;
      const pickUp = () => post("/functions/v1/pc-sign-in", { action: "pick_up", id, pickup }, anon);

      await t.step("the PC waits until the phone answers", async () => {
        assertEquals(started.body.expires_in, 120);
        assert(match >= 10 && match <= 99, String(match));
        assertEquals((await pickUp()).body, { state: "waiting" });
      });

      await t.step("the scan secret alone picks up nothing", async () => {
        const answer = await post("/functions/v1/pc-sign-in", { action: "pick_up", id, pickup: scan }, anon);
        assertEquals(answer.body, { state: "expired" });
      });

      await t.step("without a session the phone can't approve", async () => {
        const answer = await post(
          "/rest/v1/rpc/pc_sign_in_answer",
          { scan_secret: scan, approve: true, picked: match },
          anon,
        );
        assertEquals(answer.status, 401, JSON.stringify(answer.body));
      });

      await t.step("the phone sees the PC's name and approves as itself", async () => {
        const look = await post("/rest/v1/rpc/pc_sign_in_look", { scan_secret: scan }, owner);
        assertEquals(look.status, 200, JSON.stringify(look.body));
        assertEquals(look.body[0].device, "TEST-PC");
        assertEquals(look.body[0].choices.length, 3);
        assert(look.body[0].choices.includes(match));
        const answer = await post(
          "/rest/v1/rpc/pc_sign_in_answer",
          { scan_secret: scan, approve: true, picked: match },
          owner,
        );
        assertEquals(answer.body, "approved");
      });

      let code = "";
      await t.step("the PC picks up a code once", async () => {
        const answer = await pickUp();
        assertEquals(answer.body.state, "approved", JSON.stringify(answer.body));
        assertEquals(answer.body.email, EMAIL);
        code = answer.body.code;
        assert(/^\d{6}$/.test(code), code);
        assertEquals((await pickUp()).body, { state: "expired" });
      });

      await t.step("the code signs the PC in as the owner, like an emailed one", async () => {
        const verified = await post("/auth/v1/verify", { type: "email", email: EMAIL, token: code }, anon);
        assertEquals(verified.status, 200, JSON.stringify(verified.body));
        assertEquals(verified.body.user.id, OWNER);
        assert(typeof verified.body.refresh_token === "string");
        const again = await post("/auth/v1/verify", { type: "email", email: EMAIL, token: code }, anon);
        assert(again.status >= 400, "the code works once");
      });

      await t.step("a declined request says so", async () => {
        const next = await post("/functions/v1/pc-sign-in", { action: "start", device: "OTHER-PC" }, anon);
        const answer = await post(
          "/rest/v1/rpc/pc_sign_in_answer",
          { scan_secret: next.body.scan, approve: false },
          owner,
        );
        assertEquals(answer.body, "declined");
        const picked = await post("/functions/v1/pc-sign-in", {
          action: "pick_up",
          id: next.body.id,
          pickup: next.body.pickup,
        }, anon);
        assertEquals(picked.body, { state: "declined" });
      });

      await t.step("a wrong number burns the request", async () => {
        const next = await post("/functions/v1/pc-sign-in", { action: "start", device: "PHISHED-PC" }, anon);
        const wrong = next.body.match === 10 ? 11 : 10;
        const answer = await post(
          "/rest/v1/rpc/pc_sign_in_answer",
          { scan_secret: next.body.scan, approve: true, picked: wrong },
          owner,
        );
        assertEquals(answer.body, "mismatch");
        const again = await post(
          "/rest/v1/rpc/pc_sign_in_answer",
          { scan_secret: next.body.scan, approve: true, picked: next.body.match },
          owner,
        );
        assertEquals(again.body, "gone");
      });

      await t.step("one caller holds at most three open codes", async () => {
        const open = [];
        for (let i = 0; i < 3; i++) {
          open.push(await post("/functions/v1/pc-sign-in", { action: "start", device: "BUSY-PC" }, anon));
        }
        assertEquals(open.map((answer) => answer.status), [200, 200, 200]);
        const fourth = await post("/functions/v1/pc-sign-in", { action: "start", device: "BUSY-PC" }, anon);
        assertEquals(fourth.status, 429, JSON.stringify(fourth.body));
      });
    } finally {
      await sql`delete from public.pc_sign_ins where device in ('TEST-PC', 'OTHER-PC', 'PHISHED-PC', 'BUSY-PC')`;
      await sql`delete from auth.users where id = ${OWNER}`;
      await sql.end();
    }
  },
});
