// contracts/vectors/life-goals.json, the same file the Kotlin and C# tests read. The why reminder's
// groups run only in the apps.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { type LifeGoalOrderItem, orderLifeGoals, timeLeft } from "./lifeGoals.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/life-goals.json", import.meta.url)),
);

Deno.test("life-goals.json: time left", () => {
  for (const c of file.timeLeft) {
    assertEquals(timeLeft(c.by, c.today), c.expect, c.name);
  }
});

Deno.test("life-goals.json: order", () => {
  for (const c of file.order) {
    const goals = c.lifeGoals as LifeGoalOrderItem[];
    assertEquals(orderLifeGoals(goals).map((goal) => goal.id), c.expect, c.name);
  }
});
