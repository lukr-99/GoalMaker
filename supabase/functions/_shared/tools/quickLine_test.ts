// add_want, add_habit and add_goal read a short line with the bottom bar's rules (rules/quickAdd.ts,
// contracts/vectors/quick-add.json), and fields given apart win. The planner is a stub that keeps
// what it was asked to save.
import { assertEquals, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import type { GoalFields, HabitFields, Planner } from "../planner/planner.ts";
import type { WantFields } from "../planner/wantList.ts";
import { tools } from "./tools.ts";
import { wantTools } from "./wantTools.ts";

const TODAY = "2026-10-02";
const ID = "00000000-0000-4000-8000-000000000001";

function stub() {
  const saved: { habit?: HabitFields; goal?: GoalFields; want?: WantFields } = {};
  const planner = {
    now: () => Promise.resolve({ today: TODAY }),
    checkins: () => Promise.resolve([]),
    pauses: () => Promise.resolve([]),
    habits: () => Promise.resolve([]),
    tasks: () => Promise.resolve([]),
    goals: () => Promise.resolve([]),
    goalEntries: () => Promise.resolve(new Map()),
    areas: () => Promise.resolve([]),
    addHabit: (fields: HabitFields) => {
      saved.habit = fields;
      return Promise.resolve({
        id: ID,
        name: fields.name,
        emoji: null,
        cadence: fields.cadence ?? "daily",
        weekdays: fields.weekdays ?? null,
        times: fields.times ?? null,
        measure: fields.measure ?? "check",
        target: fields.target ?? null,
        direction: "at_least",
        unit: fields.unit ?? null,
        goalId: null,
        startsOn: TODAY,
        deleted: false,
        archived: false,
        showOnToday: true,
      });
    },
    addGoal: (fields: GoalFields) => {
      saved.goal = fields;
      return Promise.resolve({
        id: ID,
        title: fields.title,
        horizon: fields.horizon ?? "week",
        periodStart: "2026-09-28",
        mode: fields.mode ?? "done",
        status: "open",
        emoji: null,
        parentId: null,
        target: fields.target ?? null,
        unit: fields.unit ?? null,
        deleted: false,
      });
    },
    wants: () => ({
      add: (fields: WantFields) => {
        saved.want = fields;
        return Promise.resolve({
          id: ID,
          title: fields.title,
          reason: fields.reason,
          price: fields.price ?? null,
          currency: fields.currency ?? "CZK",
          cooldownDays: fields.days ?? 30,
          coolsUntil: "2026-10-16",
          addedOn: TODAY,
          link: null,
          areaId: null,
          madeBy: "claude",
          decision: null,
          decidedAt: null,
          decisionNote: "",
          checkedPrice: null,
          checkedAt: null,
          checkedNote: "",
        });
      },
    }),
  };
  return { planner: planner as unknown as Planner, saved };
}

function tool(name: string) {
  return [...tools, ...wantTools].find((one) => one.name === name)!;
}

Deno.test("add_habit reads how often and how much from a line", async () => {
  const { planner, saved } = stub();
  const text = await tool("add_habit").run(planner, { line: "Swim 2 times a week 40 min" });
  assertEquals(
    { ...saved.habit, startsOn: undefined },
    {
      ...saved.habit,
      name: "Swim",
      cadence: "per_week",
      times: 2,
      measure: "amount",
      target: 40,
      unit: "min",
      startsOn: undefined,
    },
  );
  assertStringIncludes(text, "Swim · 2 times a week · left · 0 of 2 this week");

  const { planner: other, saved: kept } = stub();
  await tool("add_habit").run(other, { line: "Piano every mon and thu", name: "Practice piano", cadence: "daily" });
  assertEquals(kept.habit?.name, "Practice piano", "a name given apart wins");
  assertEquals(kept.habit?.cadence, "daily", "a cadence given apart wins, with the days it brings");
  assertEquals(kept.habit?.weekdays, undefined);
});

Deno.test("add_goal reads the period and the target from a line", async () => {
  const { planner, saved } = stub();
  await tool("add_goal").run(planner, { line: "Read 3 books this month" });
  assertEquals(saved.goal?.title, "Read 3 books");
  assertEquals(saved.goal?.horizon, "month");
  assertEquals(saved.goal?.day, "2026-10-01");
  assertEquals([saved.goal?.mode, saved.goal?.target, saved.goal?.unit], ["number", 3, "books"]);

  const { planner: plain, saved: week } = stub();
  await tool("add_goal").run(plain, { line: "Call grandma" });
  assertEquals([week.goal?.title, week.goal?.horizon, week.goal?.mode], ["Call grandma", "week", "done"]);
  assertEquals(week.goal?.day, undefined, "no period named is this week, from today");

  const { planner: picked, saved: mine } = stub();
  await tool("add_goal").run(picked, { line: "Run 30 km in November", horizon: "year", target: 400 });
  assertEquals([mine.goal?.horizon, mine.goal?.target, mine.goal?.unit], ["year", 400, "km"]);
});

Deno.test("add_want reads the price, the wait and the reason from a line", async () => {
  const { planner, saved } = stub();
  const text = await tool("add_want").run(planner, {
    line: "Kindle 3 290 Kč wait 2 weeks because I read on the train",
  });
  assertEquals(saved.want?.title, "Kindle");
  assertEquals(saved.want?.reason, "I read on the train");
  assertEquals([saved.want?.price, saved.want?.currency, saved.want?.days], [3290, "CZK", 14]);
  assertStringIncludes(text, "Kindle");

  const { planner: other, saved: kept } = stub();
  await tool("add_want").run(other, { line: "Tent €120", reason: "Summer trips", price: 99 });
  assertEquals([kept.want?.title, kept.want?.reason, kept.want?.price], ["Tent", "Summer trips", 99]);
  assertEquals(kept.want?.currency, undefined, "a price given apart keeps the owner's currency");
});
