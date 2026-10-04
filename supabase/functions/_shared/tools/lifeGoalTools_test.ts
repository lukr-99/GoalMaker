// The life goals tools (docs/life-goals.md): the time left and the order come from rules/lifeGoals.ts
// (contracts/vectors/life-goals.json), worded the way the apps word them. The planner is a stub that
// keeps what it was asked to save.
import { assert, assertEquals, assertRejects, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import { type LifeGoal, type LifeGoalFields, yearsLater } from "../planner/lifeGoalList.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { orderLifeGoals } from "../rules/lifeGoals.ts";
import { lifeGoalLine, names, timeLeftText } from "./format.ts";
import { byDateOf, lifeGoalTools } from "./lifeGoalTools.ts";

const TODAY = "2026-10-04";
const ID = "00000000-0000-4000-8000-000000000001";
const HEALTH = "00000000-0000-4000-8000-0000000000a1";

function lifeGoal(fields: Partial<LifeGoal>): LifeGoal {
  return {
    id: ID,
    title: "Run a marathon",
    why: "To feel strong at 40",
    by: null,
    areaId: null,
    status: "open",
    position: 0,
    madeBy: "owner",
    createdAt: "2026-10-01T10:00:00.000000Z",
    closedAt: null,
    pictures: 0,
    ...fields,
  };
}

const areas = names([{ id: HEALTH, name: "Health", color: "green", emoji: null, archived: false }], [], new Map());

function stub(goals: LifeGoal[]) {
  const saved: { add?: LifeGoalFields; update?: LifeGoalFields } = {};
  const planner = {
    now: () => Promise.resolve({ today: TODAY }),
    areas: () => Promise.resolve([{ id: HEALTH, name: "Health", color: "green", emoji: null, archived: false }]),
    findOrCreateArea: (name: string) => Promise.resolve({ id: HEALTH, name }),
    lifeGoals: () => ({
      all: () => Promise.resolve(orderLifeGoals(goals)),
      lifeGoal: (id: string) => {
        const found = goals.find((goal) => goal.id === id);
        return found ? Promise.resolve(found) : Promise.reject(new PlannerError(`No life goal with id ${id}.`));
      },
      add: (fields: LifeGoalFields) => {
        saved.add = fields;
        return Promise.resolve(
          lifeGoal({ title: fields.title, why: fields.why, by: fields.by ?? null, madeBy: "claude" }),
        );
      },
      update: (id: string, fields: LifeGoalFields) => {
        saved.update = fields;
        const goal = goals.find((one) => one.id === id)!;
        return Promise.resolve({ ...goal, status: fields.status ?? goal.status });
      },
    }),
  };
  return { planner: planner as unknown as Planner, saved };
}

function tool(name: string) {
  return lifeGoalTools.find((one) => one.name === name)!;
}

Deno.test("time left reads like the apps", () => {
  assertEquals(timeLeftText({ unit: "years", count: 10 }), "10 years left");
  assertEquals(timeLeftText({ unit: "years", count: 1 }), "1 year left");
  assertEquals(timeLeftText({ unit: "months", count: 8 }), "8 months left");
  assertEquals(timeLeftText({ unit: "months", count: 1 }), "1 month left");
  assertEquals(timeLeftText({ unit: "days", count: 12 }), "12 days left");
  assertEquals(timeLeftText({ unit: "days", count: 1 }), "1 day left");
  assertEquals(timeLeftText({ unit: "today", count: 0 }), "Today");
  assertEquals(timeLeftText({ unit: "past", count: 0 }), "Past its date");
});

Deno.test("a life goal's line carries its time left, by date, area, maker, pictures and why", () => {
  const open = lifeGoal({ by: "2036-10-04", areaId: HEALTH, madeBy: "claude", pictures: 2 });
  assertEquals(
    lifeGoalLine(open, { unit: "years", count: 10 }, areas),
    `- Run a marathon · 10 years left · by date 2036-10-04 · @Health · by Claude · 2 pictures (life goal id ${ID})\n` +
      "  Why: To feel strong at 40",
  );
  assertEquals(
    lifeGoalLine(lifeGoal({ pictures: 1 }), null, areas).split("\n")[0],
    `- Run a marathon · 1 picture (life goal id ${ID})`,
  );
  const achieved = lifeGoal({ status: "achieved", by: "2020-01-01", closedAt: "2026-10-02T10:00:00.000000Z" });
  assertEquals(
    lifeGoalLine(achieved, { unit: "past", count: 0 }, areas).split("\n")[0],
    `- Run a marathon · achieved · by date 2020-01-01 · no pictures (life goal id ${ID})`,
  );
});

Deno.test("in N years is the same day N years later, from the planning day", async () => {
  assertEquals(yearsLater("2026-10-04", 10), "2036-10-04");
  assertEquals(yearsLater("2028-02-29", 1), "2029-02-28");
  assertEquals(yearsLater("2028-02-29", 4), "2032-02-29");
  const today = () => Promise.resolve(TODAY);
  assertEquals(await byDateOf(undefined, 5, today), "2031-10-04");
  assertEquals(await byDateOf("2030-06-01", undefined, today), "2030-06-01");
  assertEquals(await byDateOf(" ", undefined, today), null);
  assertEquals(await byDateOf(undefined, undefined, today), undefined);
  await assertRejects(() => byDateOf("2030-06-01", 5, today), PlannerError);
});

Deno.test("get_life_goals lists the open ones first, then the most recently closed", async () => {
  const goals = [
    lifeGoal({ id: "a", title: "Dropped long ago", status: "dropped", closedAt: "2026-01-01T00:00:00.000000Z" }),
    lifeGoal({ id: "b", title: "Second", position: 1, by: "2027-06-04" }),
    lifeGoal({ id: "c", title: "Achieved lately", status: "achieved", closedAt: "2026-09-01T00:00:00.000000Z" }),
    lifeGoal({ id: "d", title: "First", position: 0, by: TODAY }),
  ];
  const { planner } = stub(goals);
  const all = await tool("get_life_goals").run(planner, {});
  const titles = all.split("\n").filter((line) => line.startsWith("- ")).map((line) => line.split(" · ")[0]);
  assertEquals(titles, ["- First", "- Second", "- Achieved lately", "- Dropped long ago"]);
  assertStringIncludes(all, "Open:\n- First · Today");
  assertStringIncludes(all, "- Second · 8 months left");
  assertStringIncludes(all, "Achieved and dropped:\n- Achieved lately · achieved");
  const open = await tool("get_life_goals").run(planner, { status: "open" });
  assert(!open.includes("Achieved"), open);
  assertEquals(
    await tool("get_life_goals").run(stub([]).planner, {}),
    "There are no life goals yet. add_life_goal writes one down with why it matters.",
  );
});

Deno.test("add_life_goal counts in_years from the planning day and finds the area by name", async () => {
  const { planner, saved } = stub([]);
  const text = await tool("add_life_goal").run(planner, {
    title: "Learn Spanish",
    why: "To talk to my neighbours",
    in_years: 10,
    area: "Health",
  });
  assertEquals(saved.add, {
    title: "Learn Spanish",
    why: "To talk to my neighbours",
    by: "2036-10-04",
    areaId: HEALTH,
  });
  assertStringIncludes(text, "Added:\n- Learn Spanish · 10 years left · by date 2036-10-04 · by Claude");
});

Deno.test("update_life_goal says how the status changed and needs something to change", async () => {
  const { planner, saved } = stub([lifeGoal({})]);
  const achieved = await tool("update_life_goal").run(planner, { id: ID, status: "achieved" });
  assertStringIncludes(achieved, "Marked achieved:");
  assertEquals(saved.update?.status, "achieved");
  const cleared = await tool("update_life_goal").run(planner, { id: ID, by: "", area: "" });
  assertStringIncludes(cleared, "Updated:");
  assertEquals([saved.update?.by, saved.update?.areaId], [null, null]);
  await assertRejects(() => tool("update_life_goal").run(planner, { id: ID }), PlannerError, "Say what to change");
  const closed = stub([lifeGoal({ status: "dropped", closedAt: "2026-10-01T00:00:00.000000Z" })]);
  assertStringIncludes(await tool("update_life_goal").run(closed.planner, { id: ID, status: "open" }), "Reopened:");
});

Deno.test("no life goal tool deletes, and pictures stay in the apps", () => {
  assertEquals(lifeGoalTools.map((one) => one.name), ["get_life_goals", "add_life_goal", "update_life_goal"]);
  assert(lifeGoalTools.every((one) => !one.destructive));
  assertStringIncludes(tool("add_life_goal").description, "Pictures are added in the apps only");
});
