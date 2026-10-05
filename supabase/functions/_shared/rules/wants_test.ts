// contracts/vectors/wants.json, the same file the Kotlin and C# tests read.
import { assertAlmostEquals, assertEquals } from "jsr:@std/assert@1.0.13";
import { daysBetween } from "./day.ts";
import { planningDay } from "./planningDay.ts";
import {
  cooldownDays,
  cooldownsId,
  coolsUntil,
  DEFAULT_COOLDOWNS,
  needLate,
  openNeeds,
  progress,
  readyWants,
  type WantCooldowns,
  type WantItem,
  wantState,
  wantStats,
} from "./wants.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/wants.json", import.meta.url)),
);

function want(value: Json): WantItem {
  const until = value.coolsUntil ?? "2026-09-28";
  return {
    id: value.id ?? "w",
    title: value.title ?? "Want",
    price: value.price ?? null,
    currency: value.currency ?? "CZK",
    addedOn: value.addedOn ?? until,
    coolsUntil: until,
    decision: value.decision ?? null,
    deleted: value.deleted ?? false,
    kind: value.kind ?? "want",
    needBy: value.needBy ?? null,
  };
}

Deno.test("wants.json: the defaults", () => {
  assertEquals(file.defaults as WantCooldowns, DEFAULT_COOLDOWNS);
});

Deno.test("wants.json: every cooldown", () => {
  for (const c of file.cooldown) {
    assertEquals(
      cooldownDays(c.price, c.currency, c.cooldowns ?? DEFAULT_COOLDOWNS, c.picked ?? null, c.kind ?? "want"),
      c.expect,
      c.name,
    );
  }
});

Deno.test("wants.json: every day it cools", () => {
  for (const c of file.coolsUntil) assertEquals(coolsUntil(c.addedOn, c.days), c.expect, c.name);
});

Deno.test("wants.json: every state", () => {
  for (const c of file.state) {
    assertEquals(wantState(want(c.want), planningDay(c.now, c.dayStartHour)), c.expect, c.name);
  }
});

Deno.test("wants.json: every ring", () => {
  for (const c of file.progress) {
    const item = want(c.want);
    assertEquals(daysBetween(item.addedOn, item.coolsUntil) >= 0, true, c.name);
    assertAlmostEquals(progress(item, c.today), c.expect, 1e-9, c.name);
  }
});

Deno.test("wants.json: every notification", () => {
  for (const c of file.ready) {
    assertEquals(readyWants(c.wants.map(want), c.lastNotified, c.today).map((w) => w.id), c.expect, c.name);
  }
});

Deno.test("wants.json: every stats block", () => {
  for (const c of file.stats) {
    const stats = wantStats(c.wants.map(want), c.currency);
    assertEquals(stats.bought, c.expect.bought, c.name);
    assertEquals(stats.dropped, c.expect.dropped, c.name);
    assertAlmostEquals(stats.notSpent, c.expect.notSpent, 1e-9, c.name);
  }
});

Deno.test("wants.json: every thresholds id", async () => {
  for (const c of file.cooldownsId) assertEquals(await cooldownsId(c.owner), c.expect, c.name);
});

Deno.test("wants.json: every needs list", () => {
  for (const c of file.needs) assertEquals(openNeeds(c.wants.map(want)).map((w) => w.id), c.expect, c.name);
});

Deno.test("wants.json: every late need", () => {
  for (const c of file.needLate) assertEquals(needLate(want(c.want), c.today), c.expect, c.name);
});
