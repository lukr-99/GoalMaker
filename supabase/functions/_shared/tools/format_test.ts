// How the connector reads habits and goals out: the standings, groups and paces of the shared rules
// (contracts/vectors/habits.json and goals.json) in the words the apps use (docs/habits.md, docs/goals.md).
import { assertEquals } from "jsr:@std/assert@1.0.13";
import type { Habit } from "../planner/planner.ts";
import type { GoalItem } from "../rules/goals.ts";
import { lists } from "../rules/listRules.ts";
import type { Want } from "../planner/wantList.ts";
import {
  cadenceText,
  goalLine,
  habitLine,
  names,
  newYearLine,
  paceText,
  standingText,
  today,
  wantLine,
} from "./format.ts";
import { periodOf } from "../rules/digest.ts";

const ID = "00000000-0000-4000-8000-000000000001";

function habit(fields: Partial<Habit>): Habit {
  return {
    id: ID,
    name: "Water",
    emoji: null,
    cadence: "daily",
    weekdays: null,
    times: null,
    measure: "check",
    target: null,
    direction: "at_least",
    unit: null,
    goalId: null,
    startsOn: "2026-09-01",
    deleted: false,
    archived: false,
    showOnToday: true,
    remindAt: null,
    ...fields,
  };
}

function goal(fields: Partial<GoalItem>): GoalItem {
  return {
    id: ID,
    title: "Run",
    horizon: "week",
    periodStart: "2026-09-14",
    mode: "number",
    status: "open",
    emoji: null,
    parentId: null,
    target: 20,
    unit: "km",
    deleted: false,
    ...fields,
  };
}

const none = { value: 0, met: 0, streak: 0 };

Deno.test("a habit's line says how often it asks", () => {
  assertEquals(cadenceText(habit({})), "every day");
  assertEquals(cadenceText(habit({ cadence: "weekdays", weekdays: 21 })), "on Mon, Wed, Fri");
  assertEquals(cadenceText(habit({ cadence: "per_week", times: 3 })), "3 times a week");
  assertEquals(cadenceText(habit({ cadence: "per_month", times: 2 })), "2 times a month");
  assertEquals(cadenceText(habit({ cadence: "per_week", times: 2, direction: "at_most" })), "weekly");
  assertEquals(cadenceText(habit({ cadence: "per_month", times: 1, direction: "at_most" })), "monthly");
});

Deno.test("a habit's line says where it stands, like its card", () => {
  const water = habit({ measure: "count", target: 8, unit: "glasses" });
  assertEquals(standingText(water, { ...none, standing: "left", value: 3 }), "left · 3 of 8 glasses today");
  assertEquals(standingText(water, { ...none, standing: "done", value: 8 }), "done · 8 of 8 glasses today");
  assertEquals(standingText(habit({}), { ...none, standing: "done", value: 1 }), "done");
  assertEquals(standingText(habit({}), { ...none, standing: "skipped" }), "skipped");
  assertEquals(standingText(habit({}), { ...none, standing: "paused" }), "paused");
  assertEquals(standingText(habit({}), { ...none, standing: "none" }), "not due today");
  assertEquals(standingText(habit({ archived: true }), { ...none, standing: "none" }), "archived");

  const swim = habit({ cadence: "per_week", times: 3 });
  assertEquals(standingText(swim, { ...none, standing: "done", met: 1 }), "done · 1 of 3 this week");
  assertEquals(
    standingText(habit({ cadence: "per_month", times: 4 }), { ...none, standing: "left", met: 2 }),
    "left · 2 of 4 this month",
  );

  const snacks = habit({ direction: "at_most", measure: "count", target: 2 });
  assertEquals(standingText(snacks, { ...none, standing: "limit", value: 1 }), "limit · 1 of at most 2 today");
  assertEquals(
    standingText(snacks, { ...none, standing: "limit", value: 3 }),
    "limit · 3 of at most 2 today, over the line",
  );
  const smoke = habit({ direction: "at_most" });
  assertEquals(standingText(smoke, { ...none, standing: "limit" }), "limit · none today");
  assertEquals(standingText(smoke, { ...none, standing: "limit", value: 1 }), "limit · over the line today");

  // A weekly or monthly limit counts what its period has had so far, not just the day.
  const takeaway = habit({ direction: "at_most", cadence: "per_week", times: 2 });
  assertEquals(
    standingText(takeaway, { ...none, standing: "limit", value: 0, used: 1 }),
    "limit · 1 of at most 2 this week",
  );
  assertEquals(
    standingText(takeaway, { ...none, standing: "limit", value: 1, used: 3 }),
    "limit · 3 of at most 2 this week, over the line",
  );
  const drinks = habit({
    direction: "at_most",
    cadence: "per_month",
    times: 1,
    measure: "count",
    target: 5,
    unit: "drinks",
  });
  assertEquals(
    standingText(drinks, { ...none, standing: "limit", value: 1, used: 3 }),
    "limit · 3 of at most 5 drinks this month",
  );
  const sweets = habit({ direction: "at_most", measure: "count", target: 0 });
  assertEquals(standingText(sweets, { ...none, standing: "limit" }), "limit · none today");
  assertEquals(standingText(sweets, { ...none, standing: "limit", value: 2 }), "limit · over the line today");
  const noTakeaway = habit({ direction: "at_most", cadence: "per_week", times: 0 });
  assertEquals(standingText(noTakeaway, { ...none, standing: "limit", used: 0 }), "limit · none this week");
});

Deno.test("a habit's line carries its streak, the goal it serves and Not on Today", () => {
  const line = habitLine(
    habit({ emoji: "\u{1F3CA}", name: "Swim", cadence: "per_week", times: 2, showOnToday: false }),
    { standing: "left", value: 0, met: 1, streak: 3, serves: "Swim 100 lengths" },
  );
  assertEquals(
    line,
    `- \u{1F3CA} Swim · 2 times a week · left · 1 of 2 this week · 3-week streak · serves Swim 100 lengths · ` +
      `not on Today (habit id ${ID})`,
  );
  assertEquals(
    habitLine(habit({}), { standing: "done", value: 1, met: 1, streak: 4 }),
    `- Water · every day · done · 4-day streak (habit id ${ID})`,
  );
});

Deno.test("Today counts the habits left and says when they are all done", () => {
  const day = lists([], "2026-09-18");
  const plain = names([], [], new Map());
  const left = today(day, plain, { lines: ["- a", "- b"], keptOff: 1, left: 1, allDone: false });
  assertEquals(
    left,
    [
      "Today is Friday 18 September 2026: 0 of 0 done, 1 habit left.",
      "Nothing open is planned for today.",
      "Habits, 1 habit left:",
      "- a",
      "- b",
      "1 more habit is due today but kept off Today; get_habits lists every habit.",
    ].join("\n"),
  );
  const done = today(day, plain, { lines: ["- a"], keptOff: 0, left: 0, allDone: true });
  assertEquals(done.split("\n")[0], "Today is Friday 18 September 2026: 0 of 0 done.");
  assertEquals(done.split("\n")[2], "Habits, all done:");
  // Only limits: none is left, none is done.
  const limits = today(day, plain, { lines: ["- a"], keptOff: 0, left: 0, allDone: false });
  assertEquals(limits.split("\n")[2], "Habits:");
});

Deno.test("a goal's pace reads like its card", () => {
  assertEquals(paceText(goal({}), { pace: "on_track", behind: null }), "On track");
  assertEquals(paceText(goal({}), { pace: "behind", behind: 6 }), "Behind by 6 km");
  assertEquals(paceText(goal({ unit: null }), { pace: "behind", behind: 6 }), "Behind by 6");
  assertEquals(paceText(goal({ mode: "tasks" }), { pace: "behind", behind: 1 }), "Behind by 1 task");
  assertEquals(paceText(goal({ mode: "tasks" }), { pace: "behind", behind: 2 }), "Behind by 2 tasks");
  assertEquals(paceText(goal({ mode: "done" }), { pace: "behind", behind: null }), "Needs you");
  assertEquals(paceText(goal({}), { pace: "hit", behind: null }), "Hit");
});

Deno.test("a goal's line adds its pace and what it feeds only when asked", () => {
  const progress = { value: 8, target: 20, fraction: 0.4, hit: false };
  const plain = goalLine(goal({}), progress, "the week");
  assertEquals(plain, `- Run · week of the week · 8 of 20 km · 40% (goal id ${ID})`);
  const full = goalLine(goal({}), progress, "the week", {
    standing: { pace: "behind", behind: 2 },
    feeds: "Run 100 km",
  });
  assertEquals(full, `- Run · week of the week · 8 of 20 km · 40% · Behind by 2 km · feeds Run 100 km (goal id ${ID})`);
  const hit = goalLine(goal({}), { value: 20, target: 20, fraction: 1, hit: true }, "the week", {
    standing: { pace: "hit", behind: null },
    feeds: null,
  });
  assertEquals(hit, `- Run · week of the week · 20 of 20 km · 100% · Hit, waiting to be marked done (goal id ${ID})`);
  const done = goalLine(goal({ status: "done" }), { value: 20, target: 20, fraction: 1, hit: true }, "the week", {
    standing: { pace: "hit", behind: null },
    feeds: null,
  });
  assertEquals(done, `- Run · week of the week · 20 of 20 km · done (goal id ${ID})`);
});

Deno.test("the January nudge and a yearly digest's period read like the apps", () => {
  assertEquals(
    newYearLine({ year: 2027, review: true, goals: true }),
    "New year: 2026 has no yearly review yet and 2027 has no year goals yet.",
  );
  assertEquals(newYearLine({ year: 2027, review: false, goals: true }), "New year: 2027 has no year goals yet.");
  assertEquals(periodOf("yearly", "2026-07-14"), { kind: "yearly", start: "2026-01-01", end: "2026-12-31" });
});

Deno.test("a habit that reminds says when", () => {
  const line = habitLine(habit({ remindAt: "20:30" }), { standing: "left", value: 0, met: 0, streak: 0 });
  assertEquals(line.includes("reminds at 20:30"), true, line);
});

function want(fields: Partial<Want>): Want {
  return {
    id: ID,
    title: "Coat",
    reason: "",
    link: null,
    price: 2500,
    currency: "CZK",
    areaId: null,
    cooldownDays: 0,
    addedOn: "2026-10-02",
    coolsUntil: "2026-10-02",
    decision: null,
    decidedAt: null,
    decisionNote: "",
    checkedPrice: null,
    checkedAt: null,
    checkedNote: "",
    madeBy: "owner",
    deleted: false,
    kind: "need",
    needBy: "2026-10-20",
    ...fields,
  };
}

Deno.test("a need's line says need, its day and late once the day passed", () => {
  const none = names([], [], new Map());
  assertEquals(
    wantLine(want({}), "ready", "2026-10-20", none),
    `- Coat · need · 2500 CZK · needed by 2026-10-20 (want id ${ID})`,
  );
  assertEquals(
    wantLine(want({}), "ready", "2026-10-21", none),
    `- Coat · need · 2500 CZK · needed by 2026-10-20 · late (want id ${ID})`,
  );
  assertEquals(
    wantLine(want({ decision: "bought", reason: "Old one tore" }), "decided", "2026-10-21", none),
    `- Coat · need · 2500 CZK · needed by 2026-10-20 · bought (want id ${ID})
  Why: Old one tore`,
  );
  assertEquals(
    wantLine(want({ kind: "want", needBy: null, reason: "Warm" }), "ready", "2026-10-21", none),
    `- Coat · 2500 CZK · ready to decide since 2026-10-02 (want id ${ID})
  Why: Warm`,
  );
});
