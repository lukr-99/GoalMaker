// The review digest's JSON (docs/letter.md): the counts split project work from the rest the way the
// apps' stats do (docs/stats.md, contracts/vectors/stats.json 'weeks').
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { digest } from "../rules/digest.ts";
import type { ProjectItem } from "../rules/projects.ts";
import type { TaskItem } from "../rules/task.ts";
import { digestJson } from "./digestTools.ts";
import { names } from "./format.ts";

function done(id: string, day: string, projectId: string | null = null): TaskItem {
  return {
    id,
    title: id,
    state: "done",
    topPriority: false,
    createdAt: "2026-09-01T00:00:00.000000Z",
    plannedDate: null,
    plannedTime: null,
    areaId: null,
    recurrence: null,
    deleted: false,
    seriesId: null,
    notes: "",
    deadline: null,
    completedAt: `${day}T10:00:00.000000Z`,
    projectId,
  };
}

function project(id: string, name: string): ProjectItem {
  return {
    id,
    name,
    description: "",
    areaId: null,
    status: "active",
    repositoryUrl: null,
    localFolder: null,
    notes: "",
    position: 0,
    deleted: false,
  };
}

Deno.test("the digest counts project work apart, and a deleted project's items as other work", () => {
  const tasks = [
    done("t1", "2026-09-14", "p1"),
    done("t2", "2026-09-15", "p1"),
    done("t3", "2026-09-16"),
    // p2 was deleted: its items are plain tasks now, as the apps count them.
    done("t4", "2026-09-16", "p2"),
  ];
  const found = digest({
    period: { kind: "weekly", start: "2026-09-14", end: "2026-09-20" },
    today: "2026-09-21",
    tasks,
    completedOn: new Map(tasks.map((task) => [task.id, task.completedAt!.slice(0, 10)])),
    goals: [],
    entries: new Map(),
    habits: [],
    checkins: [],
    pauses: [],
    wants: [],
    reviews: [],
  });

  const json = digestJson(found, names([], [], new Map(), [project("p1", "GoalMaker")]), (task) => task.completedAt);

  assertEquals(json.counts, { done: 4, project_work: 2, other_work: 2 });
});
