// Runs contracts/vectors/focus.json, which the Kotlin and C# tests run too.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { appendPosition, focusBoard, focusCandidates, placeAt } from "./focus.ts";
import type { TaskItem, TaskState } from "./task.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/focus.json", import.meta.url)),
);

function tasks(rows: Json[]): TaskItem[] {
  return rows.map((row) => ({
    id: row.id,
    title: row.title ?? row.id,
    state: row.state as TaskState,
    topPriority: false,
    createdAt: row.createdAt,
    plannedDate: row.day ?? null,
    plannedTime: null,
    areaId: null,
    recurrence: null,
    deleted: row.deleted ?? false,
    seriesId: null,
    notes: row.notes ?? "",
    deadline: row.deadline ?? null,
    completedAt: null,
    focusPosition: row.focus,
  }));
}

Deno.test("focus.json: the board's order", () => {
  for (const one of file.board) {
    assertEquals(focusBoard(tasks(one.tasks)).map((task) => task.id), one.expect, one.name);
  }
});

Deno.test("focus.json: adding at the end", () => {
  for (const one of file.append) assertEquals(appendPosition(tasks(one.tasks)), one.expect, one.name);
});

Deno.test("focus.json: putting a task at a place", () => {
  for (const one of file.placeAt) assertEquals(placeAt(tasks(one.tasks), one.task, one.place), one.expect, one.name);
});

Deno.test("focus.json: what the add picker offers", () => {
  for (const one of file.candidates) {
    assertEquals(focusCandidates(tasks(one.tasks), one.query).map((task) => task.id), one.expect, one.name);
  }
});
