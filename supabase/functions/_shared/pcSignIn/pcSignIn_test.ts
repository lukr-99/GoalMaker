import { assertEquals, assertMatch, assertNotEquals } from "jsr:@std/assert@1.0.13";
import {
  callerAddress,
  callerHash,
  callerKey,
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

Deno.test("the hosted project trusts only Cloudflare's address", () => {
  const both = new Headers({ "cf-connecting-ip": "203.0.113.7", "x-forwarded-for": "198.51.100.1" });
  assertEquals(callerAddress(both, false), "203.0.113.7");
  assertEquals(callerAddress(new Headers({ "x-forwarded-for": "198.51.100.1" }), false), null);
  assertEquals(callerAddress(new Headers(), false), null);
});

Deno.test("the local stack may fall back to the gateway's forwarded address", () => {
  assertEquals(callerAddress(new Headers({ "x-forwarded-for": " 198.51.100.1, 10.0.0.2" }), true), "198.51.100.1");
  assertEquals(callerAddress(new Headers({ "cf-connecting-ip": "203.0.113.7" }), true), "203.0.113.7");
  assertEquals(callerAddress(new Headers(), true), null);
});

Deno.test("an IPv4 caller is its whole address", () => {
  assertEquals(callerKey("203.0.113.7"), "ipv4:203.0.113.7");
  assertNotEquals(callerKey("203.0.113.7"), callerKey("203.0.113.8"));
  assertEquals(callerKey("256.0.0.1"), null);
  assertEquals(callerKey("1.2.3"), null);
});

Deno.test("an IPv6 caller is its /64, however it is written", () => {
  const key = "ipv6:2001:0db8:0001:0002::/64";
  assertEquals(callerKey("2001:db8:1:2::1"), key);
  assertEquals(callerKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"), key);
  assertEquals(callerKey("2001:0DB8:0001:0002:0000:0000:0000:0042"), key);
  assertEquals(callerKey("[2001:db8:1:2::9]"), key);
  assertEquals(callerKey("2001:db8:1:2::9%eth0"), key);
  assertNotEquals(callerKey("2001:db8:1:3::1"), key);
  assertEquals(callerKey("::1"), "ipv6:0000:0000:0000:0000::/64");
});

Deno.test("an IPv4 address written as IPv6 is that IPv4 address", () => {
  assertEquals(callerKey("::ffff:203.0.113.7"), "ipv4:203.0.113.7");
  assertEquals(callerKey("::FFFF:cb00:7107"), "ipv4:203.0.113.7");
  assertEquals(callerKey("0:0:0:0:0:ffff:203.0.113.7"), "ipv4:203.0.113.7");
});

Deno.test("anything that isn't an address is no caller", () => {
  for (const text of ["", "unknown", "2001:db8::1::2", "1:2:3:4:5:6:7:8:9", "2001:db8:1:2::g", "::ffff:999.0.0.1"]) {
    assertEquals(callerKey(text), null, text);
  }
});

Deno.test("only a hash of the caller's key is kept", async () => {
  const hash = await callerHash("ipv4:203.0.113.7");
  assertMatch(hash, /^[0-9a-f]{64}$/);
  assertEquals(await callerHash("ipv4:203.0.113.7"), hash);
  assertNotEquals(await callerHash("ipv4:203.0.113.8"), hash);
});
