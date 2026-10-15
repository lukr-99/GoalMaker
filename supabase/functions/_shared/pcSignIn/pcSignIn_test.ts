import { assertEquals, assertMatch, assertNotEquals } from "jsr:@std/assert@1.0.13";
import {
  callerAddress,
  callerHash,
  deviceName,
  isMatchChoices,
  isMatchNumber,
  MAX_DEVICE,
  qrText,
  readRequest,
  scanSecretOf,
} from "./pcSignIn.ts";

const vectors = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/pc-sign-in.json", import.meta.url)),
);
const SECRET = "Qx3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_";

Deno.test("the QR text the PC builds (contracts/vectors/pc-sign-in.json)", () => {
  for (const vector of vectors.build) {
    assertEquals(qrText(vector.secret), vector.expect, vector.name);
  }
});

Deno.test("the scan secret the phone reads (contracts/vectors/pc-sign-in.json)", () => {
  for (const vector of vectors.read) {
    assertEquals(scanSecretOf(vector.text), vector.expect, vector.name);
  }
});

Deno.test("match numbers and the phone's choices (contracts/vectors/pc-sign-in.json)", () => {
  for (const vector of vectors.match.numbers) {
    assertEquals(isMatchNumber(vector.value), vector.expect, vector.name);
  }
  for (const vector of vectors.match.choices) {
    assertEquals(isMatchChoices(vector.choices), vector.expect, vector.name);
  }
});

Deno.test("a PC's name is one trimmed line of at most 64 characters", () => {
  assertEquals(deviceName("  DESKTOP-1 \r\n"), "DESKTOP-1");
  assertEquals(deviceName("My\tPC"), "My PC");
  assertEquals(deviceName("x".repeat(100))?.length, MAX_DEVICE);
  assertEquals(deviceName("   "), null);
  assertEquals(deviceName(42), null);
});

Deno.test("the function knows two calls and nothing else", () => {
  assertEquals(readRequest({ action: "start", device: "DESKTOP-1" }), { action: "start", device: "DESKTOP-1" });
  assertEquals(readRequest({ action: "start" }), null);
  const id = "c0ffee00-0000-4000-8000-000000000001";
  assertEquals(readRequest({ action: "pick_up", id, pickup: SECRET }), { action: "pick_up", id, pickup: SECRET });
  assertEquals(readRequest({ action: "pick_up", id: "1", pickup: SECRET }), null);
  assertEquals(readRequest({ action: "pick_up", id, pickup: "short" }), null);
  assertEquals(readRequest({ action: "approve", id, pickup: SECRET }), null);
  assertEquals(readRequest(null), null);
});

Deno.test("the caller is Cloudflare's address first, then the first forwarded one", () => {
  assertEquals(
    callerAddress(new Headers({ "cf-connecting-ip": "203.0.113.7", "x-forwarded-for": "198.51.100.1" })),
    "203.0.113.7",
  );
  assertEquals(callerAddress(new Headers({ "x-forwarded-for": " 198.51.100.1, 10.0.0.2" })), "198.51.100.1");
  assertEquals(callerAddress(new Headers()), "unknown");
});

Deno.test("only a hash of the caller's address is kept", async () => {
  const hash = await callerHash("203.0.113.7");
  assertMatch(hash, /^[0-9a-f]{64}$/);
  assertEquals(await callerHash("203.0.113.7"), hash);
  assertNotEquals(await callerHash("203.0.113.8"), hash);
});
