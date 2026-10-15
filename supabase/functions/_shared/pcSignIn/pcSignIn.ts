// The PC's QR sign-in (ADR 0020, docs/sign-in.md): what the pc-sign-in function accepts, the text in
// the QR code and the match numbers (contracts/vectors/pc-sign-in.json, which the apps run too).

export const QR_PREFIX = "goalmaker://sign-in/pc/";
const SECRET = /^[A-Za-z0-9_-]{43}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const MAX_DEVICE = 64;
export const MATCH_CHOICES = 3;

export type PcSignInRequest =
  | { action: "start"; device: string }
  | { action: "pick_up"; id: string; pickup: string };

/** The text the PC puts in its QR code. */
export function qrText(scanSecret: string): string {
  return QR_PREFIX + scanSecret;
}

/** The scan secret in a scanned text, or null when it is not a GoalMaker PC sign-in. */
export function scanSecretOf(text: string): string | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith(QR_PREFIX)) return null;
  const secret = trimmed.slice(QR_PREFIX.length);
  return SECRET.test(secret) ? secret : null;
}

/** A match number is two digits: 10 to 99. */
export function isMatchNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 10 && value <= 99;
}

/** The phone's choices are three different match numbers. */
export function isMatchChoices(values: unknown): values is number[] {
  return Array.isArray(values) && values.length === MATCH_CHOICES && values.every(isMatchNumber) &&
    new Set(values).size === MATCH_CHOICES;
}

/** The PC's name as a one-line label, or null when nothing is left of it. */
export function deviceName(given: unknown): string | null {
  if (typeof given !== "string") return null;
  // deno-lint-ignore no-control-regex
  const name = given.replace(/[\u0000-\u001f\u007f]+/g, " ").replace(/\s+/g, " ").trim();
  return name.length === 0 ? null : [...name].slice(0, MAX_DEVICE).join("");
}

/** The call in a request body, or null when it isn't one the function knows. */
export function readRequest(body: unknown): PcSignInRequest | null {
  const fields = (body ?? {}) as Record<string, unknown>;
  if (fields.action === "start") {
    const device = deviceName(fields.device);
    return device === null ? null : { action: "start", device };
  }
  if (fields.action === "pick_up") {
    const { id, pickup } = fields;
    if (typeof id !== "string" || !UUID.test(id) || typeof pickup !== "string" || !SECRET.test(pickup)) return null;
    return { action: "pick_up", id, pickup };
  }
  return null;
}

/**
 * The caller's address, for the per-caller limits, or null when there is none to trust. The hosted
 * project sits behind Cloudflare, which sets cf-connecting-ip itself and drops one a client sends,
 * so that is the only header trusted there; x-forwarded-for is whatever the client wrote. On the
 * local stack ([localStack]) nothing is in front but the gateway, which adds the address it saw to
 * x-forwarded-for, so a missing cf-connecting-ip falls back to that, and only there.
 */
export function callerAddress(headers: Headers, localStack: boolean): string | null {
  const cloudflare = headers.get("cf-connecting-ip")?.trim();
  if (cloudflare) return cloudflare;
  if (!localStack) return null;
  return headers.get("x-forwarded-for")?.split(",")[0]?.trim() || null;
}

/** An IPv4 address as four numbers, or null. */
function ipv4Of(text: string): number[] | null {
  const parts = text.split(".");
  if (parts.length !== 4 || !parts.every((part) => /^\d{1,3}$/.test(part))) return null;
  const numbers = parts.map(Number);
  return numbers.every((number) => number <= 255) ? numbers : null;
}

/** An IPv6 address as eight 16-bit groups, or null. Takes "::", a zone and a dotted IPv4 tail. */
function ipv6Of(text: string): number[] | null {
  let address = text.replace(/^\[|\]$/g, "").replace(/%.*$/, "").toLowerCase();
  const tail = /:(\d{1,3}(?:\.\d{1,3}){3})$/.exec(address);
  if (tail) {
    const v4 = ipv4Of(tail[1]);
    if (v4 === null) return null;
    const high = ((v4[0] << 8) | v4[1]).toString(16);
    const low = ((v4[2] << 8) | v4[3]).toString(16);
    address = `${address.slice(0, tail.index)}:${high}:${low}`;
  }
  const halves = address.split("::");
  if (halves.length > 2) return null;
  const groups = (half: string) => half === "" ? [] : half.split(":");
  const head = groups(halves[0]);
  const rest = halves.length === 2 ? groups(halves[1]) : [];
  const missing = 8 - head.length - rest.length;
  if (halves.length === 2 ? missing < 1 : missing !== 0) return null;
  const all = [...head, ...Array(halves.length === 2 ? missing : 0).fill("0"), ...rest];
  if (!all.every((group) => /^[0-9a-f]{1,4}$/.test(group))) return null;
  return all.map((group) => parseInt(group, 16));
}

/**
 * What the limits count as one caller: an IPv4 address whole, an IPv6 address by its /64, since one
 * home or server gets a whole /64 and can take a new address from it for every call. An IPv4 address
 * written as IPv6 (::ffff:a.b.c.d) is that IPv4 address. Null for anything that isn't an address.
 */
export function callerKey(address: string): string | null {
  const v4 = ipv4Of(address.trim());
  if (v4 !== null) return `ipv4:${v4.join(".")}`;
  const v6 = ipv6Of(address.trim());
  if (v6 === null) return null;
  if (v6.slice(0, 5).every((group) => group === 0) && v6[5] === 0xffff) {
    return `ipv4:${[v6[6] >> 8, v6[6] & 0xff, v6[7] >> 8, v6[7] & 0xff].join(".")}`;
  }
  return `ipv6:${v6.slice(0, 4).map((group) => group.toString(16).padStart(4, "0")).join(":")}::/64`;
}

/** The caller as the database keeps it: a SHA-256 hash of its key, never the address. */
export async function callerHash(key: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`pc-sign-in:${key}`));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
