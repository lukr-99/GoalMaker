// Runs the contract vectors the Kotlin and C# tests run (contracts/vectors), against the TypeScript
// rules the connector uses, so all three agree on Today, Plan tomorrow, repeats and the archive.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { searchArchive } from "./archiveRules.ts";
import { lists } from "./listRules.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";
import { successorId, tagLinkId, toDrop } from "./occurrences.ts";
import { planningDay } from "./planningDay.ts";
import { decision, priorities, review, tomorrow } from "./planRules.ts";
import { nextOccurrence, parseRecurrence } from "./recurrence.ts";
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

Deno.test("planning day starts at the start hour", () => {
  assertEquals(planningDay("2026-09-19T03:59", 4), "2026-09-18");
  assertEquals(planningDay("2026-09-19T04:00", 4), "2026-09-19");
  assertEquals(planningDay("2026-09-19T00:30", 0), "2026-09-19");
});
