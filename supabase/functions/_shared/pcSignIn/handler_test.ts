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

function call(body: unknown, address: string | null = "203.0.113.7", forwarded?: string): Request {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (address !== null) headers["cf-connecting-ip"] = address;
  if (forwarded !== undefined) headers["x-forwarded-for"] = forwarded;
  return new Request("http://localhost/functions/v1/pc-sign-in", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

const start = { action: "start", device: "PC" };
const code = () => Promise.resolve("1");

/** Starts from each address until the store says no; how many went through. */
async function startsFrom(store: FakeStore, addresses: string[]): Promise<number[]> {
  const statuses = [];
  for (const address of addresses) statuses.push((await handle(call(start, address), store, code)).status);
  return statuses;
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

Deno.test("new addresses from one IPv6 /64 are one caller", async () => {
  const store = new FakeStore();
  const rotated = Array.from({ length: STARTS_PER_MINUTE + 1 }, (_, i) => `2001:db8:1:2::${(i + 1).toString(16)}`);
  const statuses = await startsFrom(store, rotated);
  assertEquals(statuses.slice(0, STARTS_PER_MINUTE).every((status) => status === 200), true);
  assertEquals(statuses[STARTS_PER_MINUTE], 429);
  // Another /64 is another caller.
  assertEquals(await startsFrom(store, ["2001:db8:1:3::1"]), [200]);
});

Deno.test("an IPv4 address written as IPv6 counts with the IPv4 one", async () => {
  const store = new FakeStore();
  const addresses = Array.from({ length: STARTS_PER_MINUTE }, (_, i) => i % 2 ? "203.0.113.7" : "::ffff:203.0.113.7");
  assertEquals((await startsFrom(store, addresses)).every((status) => status === 200), true);
  assertEquals(await startsFrom(store, ["::ffff:cb00:7107"]), [429]);
});

Deno.test("without a trusted address nothing goes through", async () => {
  for (const request of [call(start, null), call(start, null, "198.51.100.1"), call(start, "not an address")]) {
    const store = new FakeStore();
    const response = await handle(request, store, code);
    assertEquals(response.status, 403);
    assertEquals((await response.json()).error, "no_caller");
    assertEquals(store.callers, []);
  }
  const pickUpStore = new FakeStore();
  assertEquals((await handle(call(pickUp, null), pickUpStore, code)).status, 403);
  assertEquals(pickUpStore.finished, []);
});

Deno.test("on the local stack the gateway's forwarded address stands in", async () => {
  const response = await handle(call(start, null, "172.18.0.1"), new FakeStore(), code, true);
  assertEquals(response.status, 200);
  assertEquals((await handle(call(start, null), new FakeStore(), code, true)).status, 403);
});

Deno.test("too many open codes is busy", async () => {
  const store = new FakeStore();
  store.full = true;
  const response = await handle(call({ action: "start", device: "PC" }), store, () => Promise.resolve("1"));
  assertEquals(response.status, 429);
  assertEquals((await response.json()).error, "busy");
});
