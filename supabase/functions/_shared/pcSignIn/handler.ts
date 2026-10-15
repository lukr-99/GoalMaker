// The pc-sign-in function's calls (ADR 0020), over a store and a code maker so the tests can stand in
// for the database and Supabase Auth. index.ts wires the real ones.
import { callerAddress, callerHash, readRequest } from "./pcSignIn.ts";

export const LIFETIME_SECONDS = 120;
/** Starts a caller may make in a minute. A PC on its sign-in screen makes one every two minutes. */
export const STARTS_PER_MINUTE = 6;
/** Pick-ups a caller may make in a minute: one PC asks every two seconds, so a few PCs fit. */
export const PICK_UPS_PER_MINUTE = 120;

export interface Started {
  id: string;
  scan: string;
  pickup: string;
  match: number;
}

export interface PickedUp {
  state: "waiting" | "declined" | "expired" | "approved";
  email: string | null;
}

/** The database side (supabase/migrations/0028). */
export interface PcSignInStore {
  allow(caller: string, kind: "start" | "pick_up", perMinute: number): Promise<boolean>;
  /** Null while the caller, or everyone, holds too many open requests. */
  start(device: string, caller: string, lifetimeSeconds: number): Promise<Started | null>;
  /** An approved answer is a claim: finish it with whether the code was made. */
  pickUp(id: string, pickup: string): Promise<PickedUp>;
  finish(id: string, pickup: string, made: boolean): Promise<void>;
}

/** Makes a one-time email code for an owner, or throws. */
export type CodeMaker = (email: string) => Promise<string>;

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store", ...headers },
  });
}

function failure(status: number, error: string, message: string, headers: Record<string, string> = {}): Response {
  return json(status, { error, message }, headers);
}

export async function handle(request: Request, store: PcSignInStore, makeCode: CodeMaker): Promise<Response> {
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
    const caller = await callerHash(callerAddress(request.headers));
    const limit = call.action === "start" ? STARTS_PER_MINUTE : PICK_UPS_PER_MINUTE;
    if (!await store.allow(caller, call.action, limit)) {
      return failure(429, "rate_limited", "Too many calls from here. Wait a minute.", { "Retry-After": "60" });
    }

    if (call.action === "start") {
      const made = await store.start(call.device, caller, LIFETIME_SECONDS);
      if (made === null) return failure(429, "busy", "Too many sign-in codes are open. Try again in a minute.");
      return json(200, { ...made, expires_in: LIFETIME_SECONDS });
    }

    const found = await store.pickUp(call.id, call.pickup);
    if (found.state !== "approved") return json(200, { state: found.state });
    let code: string;
    try {
      code = await makeCode(found.email!);
    } catch (error) {
      // Let the approval go, so the PC's next ask tries again instead of losing it.
      await store.finish(call.id, call.pickup, false);
      throw error;
    }
    await store.finish(call.id, call.pickup, true);
    return json(200, { state: "approved", email: found.email, code });
  } catch (error) {
    console.error("pc-sign-in failed", error);
    return failure(502, "failed", "The sign-in couldn't go through just now. It tries again by itself.");
  }
}
