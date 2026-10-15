import { assertEquals } from "jsr:@std/assert@1.0.13";
import { deviceName, MAX_DEVICE, qrText, readRequest, scanSecretOf } from "./pcSignIn.ts";

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
