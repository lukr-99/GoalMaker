import type { Sql, TransactionSql } from "./deps.ts";

/** A transaction that runs as the owner: the `authenticated` role, the owner's claims, row security. */
export type Db = TransactionSql;

/** Who a change is logged as (supabase/migrations/0004_activity_log.sql). */
export type Actor = "claude" | "owner";

export type Resolution =
  | { kind: "owner"; ownerId: string }
  | { kind: "unknown" }
  | { kind: "limited" };

/** The secret in `.../connector/<secret>`, or null when the path isn't a connector link. */
export function secretFrom(url: URL): string | null {
  const segments = url.pathname.split("/").filter((segment) => segment.length > 0);
  const at = segments.lastIndexOf("connector");
  if (at < 0 || segments.length !== at + 2) return null;
  const secret = decodeURIComponent(segments[at + 1]);
  return /^[A-Za-z0-9_-]{20,200}$/.test(secret) ? secret : null;
}

/** Lowercase hex SHA-256, the form connector_links keeps (0007). */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * The owner of an active link, counting the call against the link's per-minute limit. This is the
 * only query the function runs outside the owner's row security, through a function made for it.
 */
export async function resolveLink(sql: Sql, secret: string, callsPerMinute = 120): Promise<Resolution> {
  const rows = await sql`
    select owner_id::text as owner_id, allowed
    from public.connector_resolve(${await sha256Hex(secret)}, ${callsPerMinute})`;
  if (rows.length === 0) return { kind: "unknown" };
  return rows[0].allowed ? { kind: "owner", ownerId: rows[0].owner_id } : { kind: "limited" };
}

/**
 * Runs `work` in one transaction as the owner: the `authenticated` role with the owner's JWT claims,
 * so every query goes through row security exactly as the apps' do, and the actor header the
 * activity log reads, so changes show who made them.
 */
export function asOwner<T>(sql: Sql, ownerId: string, actor: Actor, work: (db: Db) => Promise<T>): Promise<T> {
  return sql.begin(async (db) => {
    const claims = JSON.stringify({ sub: ownerId, role: "authenticated" });
    const headers = JSON.stringify({ "x-goalmaker-actor": actor });
    await db`select set_config('request.jwt.claims', ${claims}, true), set_config('request.headers', ${headers}, true)`;
    await db`set local role authenticated`;
    return await work(db);
  }) as Promise<T>;
}
