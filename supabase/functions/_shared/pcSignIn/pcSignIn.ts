// The PC's QR sign-in (ADR 0020, docs/sign-in.md): what the pc-sign-in function accepts, and the text
// in the QR code (contracts/vectors/pc-sign-in.json, which the apps run too).

export const QR_PREFIX = "goalmaker://sign-in/pc/";
const SECRET = /^[A-Za-z0-9_-]{43}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const MAX_DEVICE = 64;

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
