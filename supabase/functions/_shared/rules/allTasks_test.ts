// Runs contracts/vectors/all-tasks.json, which the Kotlin and C# tests run too.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { taskGroups } from "./allTasks.ts";
import type { TaskItem, TaskState } from "./task.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/all-tasks.json", import.meta.url)),
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
    areaId: row.area ?? null,
    recurrence: null,
    deleted: row.deleted ?? false,
    seriesId: null,
    notes: row.notes ?? "",
    deadline: row.deadline ?? null,
    completedAt: null,
    projectId: row.project ?? null,
  }));
}

Deno.test("all-tasks.json: the groups", () => {
  for (const one of file.groups) {
    const groups = taskGroups(
      tasks(one.tasks),
      one.areas,
      new Map(Object.entries(one.projects)) as Map<string, string | null>,
      one.query,
      one.projectItems,
    );
    assertEquals(
      groups.map((group) => ({ area: group.areaId, tasks: group.tasks.map((task) => task.id) })),
      one.expect,
      one.name,
    );
  }
});
