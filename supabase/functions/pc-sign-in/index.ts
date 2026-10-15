// The PC's QR sign-in (ADR 0020, docs/sign-in.md). The PC is not signed in, so the gateway lets the
// call through without a session (verify_jwt = false in config.toml) and this function checks nothing
// but the request's own secrets. Two calls:
//
//   {"action": "start", "device": "DESKTOP-1"}
//     -> {"id", "scan", "pickup", "expires_in"}: the scan secret goes in the QR code, the pick-up
//        secret stays on the PC.
//   {"action": "pick_up", "id", "pickup"}
//     -> {"state": "waiting" | "declined" | "expired"}, or once, after the phone approved it,
//        {"state": "approved", "email", "code"}: a one-time email code for the owner who approved,
//        made by Supabase Auth without sending any mail. The PC signs in with it like an emailed code.
//
// The phone approves through PostgREST with its own session (pc_sign_in_answer, 0028), never here.
import { postgres } from "../_shared/deps.ts";
import { readRequest } from "../_shared/pcSignIn/pcSignIn.ts";

const LIFETIME_SECONDS = 120;
const MAX_OPEN = 20;

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}

function failure(status: number, error: string, message: string): Response {
  return json(status, { error, message });
}

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

Deno.serve(async (request) => {
  if (request.method !== "POST") return failure(405, "bad_request", "Send the call with POST.");

  const call = readRequest(await request.json().catch(() => null));
  if (call === null) {
    return failure(
      400,
      "bad_request",
      'Send {"action": "start", "device": "..."} or {"action": "pick_up", "id": "...", "pickup": "..."}.',
    );
  }

  try {
    if (call.action === "start") {
      const [made] = await sql`
        select id, scan_secret, pickup_secret
        from public.pc_sign_in_start(${call.device}, ${`${LIFETIME_SECONDS} seconds`}::interval, ${MAX_OPEN})`;
      if (!made) return failure(429, "busy", "Too many PCs are asking at once. Try again in a minute.");
      return json(200, {
        id: made.id,
        scan: made.scan_secret,
        pickup: made.pickup_secret,
        expires_in: LIFETIME_SECONDS,
      });
    }

    const [found] = await sql`select state, email from public.pc_sign_in_pick_up(${call.id}, ${call.pickup})`;
    if (found.state !== "approved") return json(200, { state: found.state });
    return json(200, { state: "approved", email: found.email, code: await codeFor(found.email) });
  } catch (error) {
    console.error("pc-sign-in failed", error);
    return failure(502, "failed", "The sign-in couldn't go through just now. Make a new code.");
  }
});
