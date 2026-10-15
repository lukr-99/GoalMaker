// The PC's QR sign-in (ADR 0020, docs/sign-in.md). The PC is not signed in, so the gateway lets the
// call through without a session (verify_jwt = false in config.toml) and this function checks nothing
// but the request's own secrets, with per-caller limits. Two calls:
//
//   {"action": "start", "device": "DESKTOP-1"}
//     -> {"id", "scan", "pickup", "match", "expires_in"}: the scan secret goes in the QR code, the
//        match number is shown beside it, the pick-up secret stays on the PC.
//   {"action": "pick_up", "id", "pickup"}
//     -> {"state": "waiting" | "declined" | "expired"}, or once, after the phone approved it with the
//        right number, {"state": "approved", "email", "code"}: a one-time email code for the owner who
//        approved, made by Supabase Auth without sending any mail. The PC signs in with it like an
//        emailed code. The approval is used up only once the code is made.
//
// The phone approves through PostgREST with its own session (pc_sign_in_answer, 0028), never here.
// The logic is in _shared/pcSignIn/handler.ts; this file wires the database and Supabase Auth.
import { postgres } from "../_shared/deps.ts";
import { handle, type PcSignInStore } from "../_shared/pcSignIn/handler.ts";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });

const store: PcSignInStore = {
  async allow(caller, kind, perMinute) {
    const [row] = await sql`select public.pc_sign_in_allow(${caller}, ${kind}, ${perMinute}) as allowed`;
    return row.allowed === true;
  },
  async start(device, caller, lifetimeSeconds) {
    const [made] = await sql`
      select id, scan_secret, pickup_secret, match_number
      from public.pc_sign_in_start(${device}, ${caller}, ${`${lifetimeSeconds} seconds`}::interval)`;
    return made ? { id: made.id, scan: made.scan_secret, pickup: made.pickup_secret, match: made.match_number } : null;
  },
  async pickUp(id, pickup) {
    const [found] = await sql`select state, email from public.pc_sign_in_pick_up(${id}, ${pickup})`;
    return { state: found.state, email: found.email };
  },
  async finish(id, pickup, made) {
    await sql`select public.pc_sign_in_finish(${id}, ${pickup}, ${made})`;
  },
};

/** A one-time email code for an owner, from Supabase Auth's admin API. Nothing is mailed. */
async function codeFor(email: string): Promise<string> {
  const api = Deno.env.get("SUPABASE_URL")!;
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const response = await fetch(`${api}/auth/v1/admin/generate_link`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: key, authorization: `Bearer ${key}` },
    body: JSON.stringify({ type: "magiclink", email }),
  });
  const body = await response.json().catch(() => null);
  const code = body?.email_otp ?? body?.properties?.email_otp;
  if (!response.ok || typeof code !== "string" || code.length === 0) {
    throw new Error(`generate_link answered ${response.status}`);
  }
  return code;
}

Deno.serve((request) => handle(request, store, codeFor));
