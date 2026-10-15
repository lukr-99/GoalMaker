import { assertEquals } from "jsr:@std/assert@1.0.13";
import { handle, type PcSignInStore, PICK_UPS_PER_MINUTE, type PickedUp, STARTS_PER_MINUTE } from "./handler.ts";

const ID = "c0ffee00-0000-4000-8000-000000000001";
const PICKUP = "Ab3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_";

/** The database's rules in memory: per-caller counts, and one request whose approval is claimed. */
class FakeStore implements PcSignInStore {
  calls = new Map<string, number>();
  callers: string[] = [];
  state: PickedUp["state"] = "approved";
  claimed = false;
  pickedUp = false;
  finished: boolean[] = [];
  full = false;

  allow(caller: string, kind: "start" | "pick_up", perMinute: number): Promise<boolean> {
    this.callers.push(caller);
    const key = `${caller}/${kind}`;
    const calls = (this.calls.get(key) ?? 0) + 1;
    this.calls.set(key, calls);
    return Promise.resolve(calls <= perMinute);
  }

  start(_device: string, _caller: string) {
    return Promise.resolve(this.full ? null : { id: ID, scan: "scan", pickup: PICKUP, match: 42 });
  }

  pickUp(): Promise<PickedUp> {
    if (this.pickedUp) return Promise.resolve({ state: "expired", email: null });
    if (this.state !== "approved") return Promise.resolve({ state: this.state, email: null });
    if (this.claimed) return Promise.resolve({ state: "waiting", email: null });
    this.claimed = true;
    return Promise.resolve({ state: "approved", email: "me@example.com" });
  }

  finish(_id: string, _pickup: string, made: boolean): Promise<void> {
    this.finished.push(made);
    this.claimed = false;
    this.pickedUp = made;
    return Promise.resolve();
  }
}

function call(body: unknown, address = "203.0.113.7"): Request {
  return new Request("http://localhost/functions/v1/pc-sign-in", {
    method: "POST",
    headers: { "content-type": "application/json", "cf-connecting-ip": address },
    body: JSON.stringify(body),
  });
}

const pickUp = { action: "pick_up", id: ID, pickup: PICKUP };

Deno.test("a start gives the PC its secrets, the match number and the lifetime", async () => {
  const response = await handle(
    call({ action: "start", device: "DESKTOP-1" }),
    new FakeStore(),
    () => Promise.resolve("1"),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { id: ID, scan: "scan", pickup: PICKUP, match: 42, expires_in: 120 });
});

Deno.test("a code that couldn't be made lets the approval go, and the next ask gets it", async () => {
  const store = new FakeStore();
  let fail = true;
  const makeCode = () => fail ? Promise.reject(new Error("generate_link answered 500")) : Promise.resolve("482913");

  const first = await handle(call(pickUp), store, makeCode);
  assertEquals(first.status, 502);
  assertEquals(store.finished, [false]);

  fail = false;
  const second = await handle(call(pickUp), store, makeCode);
  assertEquals(await second.json(), { state: "approved", email: "me@example.com", code: "482913" });
  assertEquals(store.finished, [false, true]);

  const third = await handle(call(pickUp), store, makeCode);
  assertEquals(await third.json(), { state: "expired" });
});

Deno.test("an answer that isn't an approval makes no code", async () => {
  const store = new FakeStore();
  store.state = "waiting";
  let made = 0;
  const response = await handle(call(pickUp), store, () => Promise.resolve(String(++made)));
  assertEquals(await response.json(), { state: "waiting" });
  assertEquals(made, 0);
  assertEquals(store.finished, []);
});

Deno.test("one caller can't start more than its share in a minute, and others still can", async () => {
  const store = new FakeStore();
  const code = () => Promise.resolve("1");
  for (let i = 0; i < STARTS_PER_MINUTE; i++) {
    assertEquals((await handle(call({ action: "start", device: "PC" }), store, code)).status, 200);
  }
  const over = await handle(call({ action: "start", device: "PC" }), store, code);
  assertEquals(over.status, 429);
  assertEquals((await over.json()).error, "rate_limited");
  assertEquals((await handle(call({ action: "start", device: "PC" }, "198.51.100.1"), store, code)).status, 200);
  // Only hashes reach the store.
  assertEquals(store.callers.every((caller) => /^[0-9a-f]{64}$/.test(caller)), true);
});

Deno.test("polling has a limit of its own", async () => {
  const store = new FakeStore();
  store.state = "waiting";
  const code = () => Promise.resolve("1");
  for (let i = 0; i < PICK_UPS_PER_MINUTE; i++) {
    assertEquals((await handle(call(pickUp), store, code)).status, 200);
  }
  assertEquals((await handle(call(pickUp), store, code)).status, 429);
});

Deno.test("too many open codes is busy", async () => {
  const store = new FakeStore();
  store.full = true;
  const response = await handle(call({ action: "start", device: "PC" }), store, () => Promise.resolve("1"));
  assertEquals(response.status, 429);
  assertEquals((await response.json()).error, "busy");
});
