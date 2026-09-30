// contracts/vectors/reviews.json, the 'digest' group, which only the TypeScript rules read.
import { assertAlmostEquals, assertEquals } from "jsr:@std/assert@1.0.13";
import { defaultPeriod, type Digest, digest, type DigestInput, type DigestKind } from "./digest.ts";
import { planningDay } from "./planningDay.ts";
import type { TaskItem } from "./task.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/reviews.json", import.meta.url)),
);

for (const vector of file.digest.periods) {
  Deno.test(`reviews.json digest period: ${vector.name}`, () => {
    const period = defaultPeriod(vector.kind as DigestKind, planningDay(vector.local, vector.startHour));
    assertEquals({ start: period.start, end: period.end }, { start: vector.start, end: vector.end });
  });
}

function task(value: Json): TaskItem {
  return {
    id: value.id,
    title: value.title,
    state: value.state ?? "open",
    topPriority: false,
    createdAt: "2026-08-01T00:00:00.000000Z",
    plannedDate: value.plannedDate ?? null,
    plannedTime: null,
    areaId: value.areaId ?? null,
    recurrence: null,
    deleted: false,
    seriesId: null,
    notes: "",
    deadline: value.deadline ?? null,
    completedAt: value.completedAt ?? null,
    goalId: value.goalId ?? null,
    movedCount: value.movedCount ?? 0,
    projectId: value.projectId ?? null,
  };
}

function input(week: Json): DigestInput {
  return {
    period: week.period,
    today: week.today,
    tasks: week.tasks.map(task),
    completedOn: new Map(Object.entries(week.completedOn)),
    goals: week.goals.map((goal: Json) => ({
      id: goal.id,
      title: goal.title,
      horizon: goal.horizon,
      periodStart: goal.periodStart,
      mode: goal.mode,
      status: goal.status ?? "open",
      emoji: null,
      parentId: null,
      target: goal.target ?? null,
      unit: goal.unit ?? null,
      deleted: false,
    })),
    entries: new Map(
      Object.entries(week.entries).map((
        [goal, amounts],
      ) => [goal, (amounts as number[]).map((amount) => ({ amount, deleted: false }))]),
    ),
    habits: week.habits.map((habit: Json) => ({
      id: habit.id,
      name: habit.name,
      emoji: null,
      archived: false,
      cadence: habit.cadence,
      weekdays: null,
      times: null,
      measure: "check",
      target: null,
      direction: "at_least",
      unit: null,
      goalId: null,
      startsOn: habit.startsOn,
      deleted: false,
    })),
    checkins: week.checkins.map((checkin: Json) => ({
      habitId: checkin.habitId,
      day: checkin.day,
      value: checkin.value,
      skipped: checkin.skipped ?? false,
      deleted: false,
    })),
    pauses: [],
    wants: week.wants.map((want: Json) => ({
      id: want.id,
      title: want.title,
      price: null,
      currency: "CZK",
      addedOn: "2026-07-01",
      coolsUntil: want.coolsUntil,
      decision: want.decision ?? null,
      decidedOn: want.decidedOn ?? null,
      deleted: false,
    })),
    reviews: week.reviews.map((review: Json) => ({
      kind: review.kind,
      periodStart: review.periodStart,
      mood: review.mood ?? null,
      energy: review.energy ?? null,
      summary: review.summary,
      reflections: [],
    })),
  };
}

const ids = (items: { id: string }[]) => items.map((item) => item.id);

Deno.test("reviews.json digest: a fixed week, section by section", () => {
  const week = file.digest.week;
  const expect = week.expect;
  const found: Digest = digest(input(week));
  assertEquals(ids(found.done), expect.done);
  assertEquals(ids(found.left), expect.left);
  assertEquals(ids(found.earlier), expect.earlier);
  assertEquals(ids(found.overdue), expect.overdue);
  assertEquals(ids(found.slipping), expect.slipping);
  assertEquals(found.goals.length, expect.goals.length);
  found.goals.forEach((row, index) => {
    assertEquals(row.goal.id, expect.goals[index].id);
    assertEquals(row.progress.value, expect.goals[index].value);
    assertAlmostEquals(row.progress.fraction, expect.goals[index].fraction);
    assertAlmostEquals(row.expected, expect.goals[index].expected);
  });
  assertEquals(
    found.habits.map((row) => ({
      id: row.habit.id,
      met: row.met,
      missed: row.missed,
      skipped: row.skipped,
      periods: row.periods,
      streak: row.streak,
    })),
    expect.habits,
  );
  assertEquals(ids(found.finishedItems), expect.finished);
  assertEquals(found.triggers, expect.triggers);
  assertEquals(found.review?.periodStart ?? null, expect.review);
  assertEquals(found.lastLetter?.periodStart ?? null, expect.lastLetter);
  assertEquals({ start: found.next.start, end: found.next.end }, expect.next);
  assertEquals(ids(found.nextTasks), expect.nextTasks);
  assertEquals(ids(found.nextDeadlines), expect.nextDeadlines);
  assertEquals(ids(found.nextGoals), expect.nextGoals);
  assertEquals(ids(found.wantsReady), expect.wantsReady);
  assertEquals(ids(found.wantsDecided), expect.wantsDecided);
  assertEquals(ids(found.wantsReadyNext), expect.wantsReadyNext);
});
