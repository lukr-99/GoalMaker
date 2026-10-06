// Project item ids in the planner and the tools (docs/projects.md, "Item ids"). The planner runs on a
// stub database that answers every query with the rows it is given; the numbering itself runs against
// the database in supabase/tests/database/0027_project_item_ids.test.sql and connector/endpoint_test.ts.
import { assertEquals, assertRejects, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import type { Db } from "../owner.ts";
import { Planner, PlannerError, type ProjectFields } from "../planner/planner.ts";
import type { ProjectItem } from "../rules/projects.ts";
import type { TaskItem } from "../rules/task.ts";
import { tools } from "./tools.ts";

const TASK = "00000000-0000-4000-8000-000000000001";
const PROJECT = "00000000-0000-4000-8000-0000000000a1";

// A database that answers every query with `rows` and keeps the values each query was given.
function stubDb(rows: unknown[]) {
  const asked: unknown[][] = [];
  const db = ((_strings: TemplateStringsArray, ...values: unknown[]) => {
    asked.push(values);
    return Promise.resolve(rows);
  }) as unknown as Db;
  return { db, asked };
}

Deno.test("a task id stays as it is, and anything else that isn't an item id finds nothing", async () => {
  const { db, asked } = stubDb([]);
  const planner = new Planner(db);
  assertEquals(await planner.taskId(` ${TASK} `), TASK);
  assertEquals(await planner.taskId("Fix the widget"), null);
  assertEquals(await planner.taskId("GM-0"), null);
  assertEquals(asked.length, 0, "no query for either");
});

Deno.test("an item id is looked up by its key and number, in any case", async () => {
  const { db, asked } = stubDb([{ id: TASK }]);
  assertEquals(await new Planner(db).taskId("gm-12"), TASK);
  assertEquals(asked, [["GM", 12]]);
});

Deno.test("#12 needs the project it is in", async () => {
  const { db, asked } = stubDb([{ id: TASK }]);
  const planner = new Planner(db);
  await assertRejects(() => planner.taskId("#12"), PlannerError, "like GM-12");
  assertEquals(await planner.taskId("#12", PROJECT), TASK);
  assertEquals(asked, [[PROJECT, 12]]);
});

function project(fields: Partial<ProjectItem>): ProjectItem {
  return {
    id: PROJECT,
    name: "GoalMaker",
    description: "",
    areaId: null,
    status: "active",
    repositoryUrl: null,
    localFolder: null,
    notes: "",
    position: 0,
    deleted: false,
    itemKey: "GM",
    ...fields,
  };
}

const item: TaskItem = {
  id: TASK,
  title: "Fix the widget",
  state: "open",
  topPriority: false,
  createdAt: "2026-10-01T08:00:00.000000Z",
  plannedDate: null,
  plannedTime: null,
  areaId: null,
  recurrence: null,
  deleted: false,
  seriesId: null,
  notes: "",
  deadline: null,
  completedAt: null,
  projectId: PROJECT,
  itemType: "bug",
  boardColumn: "todo",
  priority: "normal",
  itemNumber: 12,
};

function stubPlanner() {
  const saved: { add?: ProjectFields; update?: ProjectFields; asked?: string } = {};
  const planner = {
    tasks: () => Promise.resolve([item]),
    task: (reference: string) => {
      saved.asked = reference;
      return Promise.resolve(reference.trim().toUpperCase() === "GM-12" ? item : null);
    },
    areas: () => Promise.resolve([]),
    tags: () => Promise.resolve([]),
    tagLinks: () => Promise.resolve(new Map()),
    projects: () => Promise.resolve([project({})]),
    milestones: () => Promise.resolve([]),
    findProject: () => Promise.resolve(project({})),
    addProject: (fields: ProjectFields) => {
      saved.add = fields;
      return Promise.resolve(project({ itemKey: fields.itemKey?.toUpperCase() ?? "GM" }));
    },
    updateProject: (_id: string, fields: ProjectFields) => {
      saved.update = fields;
      return Promise.resolve(project({ itemKey: fields.itemKey ?? "GM" }));
    },
  } as unknown as Planner;
  return { planner, saved };
}

function tool(name: string) {
  return tools.find((one) => one.name === name)!;
}

Deno.test("search_tasks finds an item by its id", async () => {
  const { planner, saved } = stubPlanner();
  const found = await tool("search_tasks").run(planner, { query: "gm-12" });
  assertStringIncludes(found, "Item:\n- [ ] GM-12 Fix the widget · +GoalMaker · bug");
  assertEquals(saved.asked, "gm-12");
  assertEquals(await tool("search_tasks").run(planner, { query: "#12" }), 'Nothing matches "#12".');
});

Deno.test("create_project and update_project pass the key on", async () => {
  const { planner, saved } = stubPlanner();
  const made = await tool("create_project").run(planner, { name: "GoalMaker", item_key: "gx" });
  assertEquals(saved.add?.itemKey, "gx");
  assertStringIncludes(made, "key GX");
  await tool("create_project").run(planner, { name: "GoalMaker" });
  assertEquals(saved.add?.itemKey, undefined, "left out, the planner suggests one");
  await tool("update_project").run(planner, { project: "GoalMaker", item_key: "" });
  assertEquals(saved.update?.itemKey, "", "empty clears it");
});

Deno.test("get_projects shows each project's key", async () => {
  const { planner } = stubPlanner();
  assertStringIncludes(await tool("get_projects").run(planner, {}), "GoalMaker · active · 1 open of 1 · key GM");
});
