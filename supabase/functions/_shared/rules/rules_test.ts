// Runs the contract vectors the Kotlin and C# tests run (contracts/vectors), against the TypeScript
// rules the connector uses, so all three agree on Today, Plan tomorrow, repeats and the archive.
import { assert, assertEquals } from "jsr:@std/assert@1.0.13";
import { searchArchive } from "./archiveRules.ts";
import {
  canServe,
  goalCopies,
  type GoalItem,
  goalProgress,
  periodEnd,
  periodStart as goalPeriodStart,
} from "./goals.ts";
import {
  checkinId,
  goalAmounts,
  type HabitItem,
  habitPeriodEnd,
  habitPeriodStart,
  habitState,
  heat,
  isDue,
  ring,
  streak,
} from "./habits.ts";
import { lists } from "./listRules.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";
import { successorId, tagLinkId, toDrop } from "./occurrences.ts";
import { planningDay } from "./planningDay.ts";
import { decision, priorities, review, tomorrow } from "./planRules.ts";
import { nextOccurrence, parseRecurrence } from "./recurrence.ts";
import { periodStart, reviewId } from "./reviews.ts";
import type { TaskItem, TaskState } from "./task.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

async function vectors(name: string): Promise<Json> {
  return JSON.parse(await Deno.readTextFile(new URL(`../../../../contracts/vectors/${name}`, import.meta.url)));
}

function task(fields: Json, index: number): TaskItem {
  return {
    id: fields.id,
    title: fields.title ?? fields.id,
    state: (fields.status ?? "open") as TaskState,
    topPriority: fields.top ?? false,
    createdAt: `2026-09-10T08:${String(index).padStart(2, "0")}:00.000000Z`,
    plannedDate: fields.planned ?? null,
    plannedTime: fields.time ?? null,
    areaId: fields.area ?? null,
    recurrence: null,
    deleted: fields.deleted ?? false,
    seriesId: fields.series ?? null,
    notes: fields.notes ?? "",
    deadline: null,
    completedAt: fields.completedAt ?? null,
  };
}

const ids = (items: TaskItem[]) => items.map((item) => item.id);

Deno.test("lists.json: every list", async () => {
  const file = await vectors("lists.json");
  for (const vector of file.cases) {
    const now = vector.now ?? file.now;
    const startHour = vector.rolloverHour ?? file.rolloverHour;
    const tasks = vector.tasks.map((fields: Json, index: number) => task({ ...file.taskDefaults, ...fields }, index));
    const result = lists(tasks, planningDay(now, startHour));
    const expect = vector.expect;
    assertEquals(
      {
        priorities: ids(result.todaySections.priorities),
        scheduled: ids(result.todaySections.scheduled),
        more: ids(result.todaySections.more),
        overdue: ids(result.todaySections.overdue),
        tomorrow: ids(result.tomorrow),
        inbox: ids(result.inbox),
        summary: result.summary,
      },
      {
        priorities: expect.priorities ?? [],
        scheduled: expect.scheduled ?? [],
        more: expect.more ?? [],
        overdue: expect.overdue ?? [],
        tomorrow: expect.tomorrow ?? [],
        inbox: expect.inbox ?? [],
        summary: { done: expect.summary?.done ?? 0, total: expect.summary?.total ?? 0 },
      },
      vector.name,
    );
  }
});

Deno.test("plan.json: every step and decision", async () => {
  const file = await vectors("plan.json");
  for (const vector of file.cases) {
    const today = planningDay(vector.now ?? file.now, vector.rolloverHour ?? file.rolloverHour);
    const tasks: TaskItem[] = vector.tasks.map((fields: Json, index: number) =>
      task({ ...file.taskDefaults, ...fields }, index)
    );
    const expect = vector.expect;
    assertEquals(ids(review(tasks, today)), expect.review ?? [], `${vector.name}: review`);
    assertEquals(ids(tomorrow(tasks, today)), expect.tomorrow ?? [], `${vector.name}: tomorrow`);
    assertEquals(priorities(tasks, today), expect.priorities ?? 0, `${vector.name}: priorities`);
    for (const [id, expected] of Object.entries(expect.decisions ?? {})) {
      assertEquals(decision(tasks.find((item) => item.id === id)!, today), expected, `${vector.name}: ${id}`);
    }
  }
});

Deno.test("recurrence.json: next days, ids and repair", async () => {
  const file = await vectors("recurrence.json");
  for (const vector of file.next) {
    const rule = parseRecurrence(vector.rule);
    const next = rule === null ? null : nextOccurrence(rule, vector.planned, vector.today);
    assertEquals(next, vector.next, vector.name);
  }
  for (const vector of file.successors) {
    assertEquals(await successorId(vector.id), vector.successor, vector.id);
  }
  for (const vector of file.tagLinks) {
    assertEquals(await tagLinkId(vector.task, vector.tag), vector.id, vector.id);
  }
  for (const vector of file.repair) {
    const tasks = vector.occurrences.map((fields: Json, index: number) => task(fields, index));
    // Which occurrences go is the contract, not the order they are named in.
    assertEquals(toDrop(tasks).sort(), [...vector.drop].sort(), vector.name);
  }
});

Deno.test("archive.json: every search", async () => {
  const file = await vectors("archive.json");
  for (const vector of file.cases) {
    const tasks = vector.tasks.map((fields: Json, index: number) => task(fields, index));
    assertEquals(ids(searchArchive(tasks, vector.query)), vector.keep, vector.name);
  }
});

Deno.test("reminders.json: ritual run ids", async () => {
  const file = await vectors("reminders.json");
  for (const vector of file.ritualIds) {
    const name = `${vector.owner.toLowerCase()}/${vector.ritual}/${vector.day}`;
    assertEquals(await nameBasedUuid(file.runNamespace, name), vector.id, name);
  }
});

Deno.test("reviews.json: review ids and periods", async () => {
  const file = await vectors("reviews.json");
  for (const vector of file.ids) {
    assertEquals(await reviewId(vector.owner, vector.kind, vector.periodStart), vector.id, vector.id);
  }
  for (const vector of file.periods) {
    assertEquals(periodStart(vector.kind, vector.day), vector.start, `${vector.kind} ${vector.day}`);
  }
});

Deno.test("goals.json: periods, parents, progress and copies", async () => {
  const file = await vectors("goals.json");
  for (const vector of file.periods) {
    const start = goalPeriodStart(vector.horizon, vector.day);
    assertEquals(start, vector.start, `${vector.horizon} ${vector.day}`);
    assertEquals(periodEnd(vector.horizon, start), vector.end, `${vector.horizon} ${vector.day} end`);
  }
  for (const vector of file.parents) {
    assertEquals(
      canServe(vector.child.horizon, vector.child.start, vector.parent.horizon, vector.parent.start),
      vector.allowed,
      vector.name,
    );
  }
  for (const vector of file.progress) {
    const tasks = vector.tasks.map((fields: Json, index: number) => task({ id: `t${index}`, ...fields }, index));
    const entries = vector.entries.map((entry: Json) => ({ amount: entry.amount, deleted: entry.deleted ?? false }));
    const progress = goalProgress(vector.goal.mode, vector.goal.status, vector.goal.target ?? null, tasks, entries);
    for (const key of ["value", "target", "fraction"] as const) {
      assert(Math.abs(progress[key] - vector.expect[key]) < 1e-9, `${vector.name}: ${key} ${progress[key]}`);
    }
    assertEquals(progress.hit, vector.expect.hit, vector.name);
  }
  for (const vector of file.copy) {
    const goal = (fields: Json, horizon: string, start: string) => ({
      id: fields.id,
      title: fields.title ?? "Parent",
      horizon,
      periodStart: start,
      mode: "done",
      status: fields.status ?? "open",
      emoji: null,
      parentId: fields.parent ?? null,
      target: null,
      unit: null,
      deleted: false,
    });
    const goals = vector.goals.map((fields: Json) => goal(fields, vector.to.horizon, vector.to.start));
    const parents = new Map<string, GoalItem>(
      vector.parents.map((fields: Json) => [fields.id, goal(fields, fields.horizon, fields.start)]),
    );
    assertEquals(
      goalCopies(goals, parents, vector.to.horizon, vector.to.start).map((copy) => ({
        title: copy.title,
        parent: copy.parentId,
      })),
      vector.expect,
      vector.name,
    );
  }
});

Deno.test("habits.json: due days, periods, states, streaks, heat, rings, ids and goal amounts", async () => {
  const file = await vectors("habits.json");
  const habit = (fields: Json): HabitItem => ({
    id: "h",
    unit: null,
    goalId: null,
    deleted: false,
    ...fields,
  });
  const checkins = (vector: Json) =>
    vector.checkins.map((fields: Json) => ({ habitId: "h", deleted: false, ...fields }));
  const pauses = (vector: Json) => vector.pauses.map((fields: Json) => ({ deleted: false, ...fields }));
  for (const vector of file.due) {
    assertEquals(isDue(habit(vector.habit), vector.day), vector.expect, vector.name);
  }
  for (const vector of file.periods) {
    const start = habitPeriodStart(habit(vector.habit), vector.day);
    assertEquals(start, vector.start, `${vector.habit.cadence} ${vector.day}`);
    assertEquals(habitPeriodEnd(habit(vector.habit), start), vector.end, `${vector.habit.cadence} ${vector.day} end`);
  }
  for (const vector of file.states) {
    const state = habitState(habit(vector.habit), vector.period, vector.today, checkins(vector), pauses(vector));
    assertEquals(state, vector.expect, vector.name);
  }
  for (const vector of file.streaks) {
    assertEquals(
      streak(habit(vector.habit), vector.today, checkins(vector), pauses(vector)),
      vector.expect,
      vector.name,
    );
  }
  for (const vector of file.heat) {
    const value = heat(habit(vector.habit), vector.day, checkins(vector), pauses(vector));
    if (typeof vector.expect === "number") {
      assert(typeof value === "number" && Math.abs(value - vector.expect) < 1e-9, `${vector.name}: ${value}`);
    } else {
      assertEquals(value, vector.expect, vector.name);
    }
  }
  for (const vector of file.rings) {
    const value = ring(habit(vector.habit), vector.today, checkins(vector));
    if (vector.expect === null) {
      assertEquals(value, null, vector.name);
    } else {
      assert(value !== null && Math.abs(value - vector.expect) < 1e-9, `${vector.name}: ${value}`);
    }
  }
  for (const vector of file.checkinIds) {
    assertEquals(await checkinId(vector.habitId, vector.day), vector.expect, vector.habitId);
  }
  for (const vector of file.goalAmounts) {
    const goal = { ...vector.goal, periodStart: vector.goal.start };
    const habits = vector.habits.map((fields: Json) => ({ deleted: false, ...fields }));
    assertEquals(goalAmounts(goal, habits, checkins(vector)), vector.expect, vector.name);
  }
});

Deno.test("planning day starts at the start hour", () => {
  assertEquals(planningDay("2026-09-19T03:59", 4), "2026-09-18");
  assertEquals(planningDay("2026-09-19T04:00", 4), "2026-09-19");
  assertEquals(planningDay("2026-09-19T00:30", 0), "2026-09-19");
});
