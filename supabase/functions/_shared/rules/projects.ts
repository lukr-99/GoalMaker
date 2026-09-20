import { compareText, type TaskItem, type TaskState } from "./task.ts";

/**
 * The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
 * how a column and the task's own state move together, and the order items sit in.
 */
export type BoardColumn = "backlog" | "todo" | "doing" | "done";
export type ItemType = "task" | "idea" | "bug";
export type Priority = "low" | "normal" | "high" | "urgent";
export type ProjectStatus = "active" | "paused" | "done";

/** The four columns, left to right. */
export const COLUMNS: BoardColumn[] = ["backlog", "todo", "doing", "done"];

/** The priorities, most important first. */
export const PRIORITIES: Priority[] = ["urgent", "high", "normal", "low"];

const RANK: Record<string, number> = { urgent: 3, high: 2, normal: 1, low: 0 };

export interface ProjectItem {
  id: string;
  name: string;
  description: string;
  areaId: string | null;
  status: ProjectStatus;
  repositoryUrl: string | null;
  localFolder: string | null;
  notes: string;
  position: number;
  deleted: boolean;
}

export interface ProjectMilestone {
  id: string;
  projectId: string;
  name: string;
  position: number;
  deleted: boolean;
}

/** One column with the items in it, in the order the board shows them. */
export interface Column {
  column: BoardColumn;
  items: TaskItem[];
}

/** The column a new item of this type lands in: an idea in the backlog, anything else in to do. */
export function columnFor(itemType: string): BoardColumn {
  return itemType === "idea" ? "backlog" : "todo";
}

/** How important a priority is; an unknown one counts as normal. */
export function rank(priority: string | undefined): number {
  return RANK[priority ?? "normal"] ?? 1;
}

/** What moving an item to a column does to a task in this state. */
export function moved(column: string, state: TaskState): TaskState {
  if (state === "dropped") return state;
  if (column === "done") return "done";
  return state === "done" ? "open" : state;
}

/** What finishing or reopening a task does to the column it sits in. */
export function finishedIn(state: TaskState, column: string): string {
  if (state === "done") return "done";
  return state === "open" && column === "done" ? "todo" : column;
}

/** One column's items in the order the board shows them. */
export function order(items: TaskItem[]): TaskItem[] {
  return [...items].sort((left, right) =>
    rank(right.priority) - rank(left.priority) ||
    (left.position ?? 0) - (right.position ?? 0) ||
    compareText(left.createdAt, right.createdAt) ||
    compareText(left.id, right.id)
  );
}

/** The four columns of a project with their items in order; a column with nothing in it stays. */
export function board(items: TaskItem[]): Column[] {
  return COLUMNS.map((column) => ({
    column,
    items: order(items.filter((item) => !item.deleted && item.boardColumn === column)),
  }));
}

/**
 * A repository URL in the form every way of writing it shares: `github.com/owner/app` for
 * `https://github.com/Owner/app.git`, `git@github.com:Owner/app` and `https://github.com/owner/app/`.
 * Null when there is nothing to compare.
 */
export function repositoryKey(url: string | null | undefined): string | null {
  const text = (url ?? "").trim();
  if (text === "") return null;
  const scp = /^[A-Za-z0-9._-]+@([A-Za-z0-9._-]+):(.+)$/.exec(text);
  const rest = scp !== null
    ? `${scp[1]}/${scp[2].replace(/^\/+/, "")}`
    : text.replace(/^[A-Za-z][A-Za-z0-9+.-]*:\/\//, "").replace(/^[^/@]+@/, "");
  return trimTail(rest.toLowerCase()) || null;
}

/** A folder in the form both slashes share: `f:/goalmaker` for `F:\GoalMaker\`. Null when empty. */
export function folderKey(path: string | null | undefined): string | null {
  const key = (path ?? "").trim().replace(/[\\/]+/g, "/").replace(/\/+$/, "").toLowerCase();
  return key === "" ? null : key;
}

/**
 * The project a reference points at: its id, its repository URL, a folder inside it, or its name
 * (docs/projects.md). This is how Claude Code names the project of the folder it works in, without
 * the owner having to say which one it is (spec, story 76).
 */
export function matchProject(projects: ProjectItem[], reference: string): ProjectItem | null {
  const text = reference.trim();
  if (text === "") return null;
  const live = projects.filter((project) => !project.deleted);

  const byId = live.find((project) => project.id === text);
  if (byId !== undefined) return byId;

  const repository = repositoryKey(text);
  const byRepository = repository === null
    ? undefined
    : live.find((project) => repositoryKey(project.repositoryUrl) === repository);
  if (byRepository !== undefined) return byRepository;

  // The deepest folder that holds the reference wins, so a project inside another project's folder
  // still gets its own items.
  const folder = folderKey(text);
  let inFolder: ProjectItem | null = null;
  let depth = -1;
  for (const project of live) {
    const own = folderKey(project.localFolder);
    if (folder === null || own === null) continue;
    if ((folder === own || folder.startsWith(`${own}/`)) && own.length > depth) {
      inFolder = project;
      depth = own.length;
    }
  }
  if (inFolder !== null) return inFolder;

  const name = text.toLowerCase();
  return live.find((project) => project.name.trim().toLowerCase() === name) ?? null;
}

// Trailing slashes and a trailing .git, however they are stacked up.
function trimTail(text: string): string {
  let out = text;
  for (;;) {
    const next = out.replace(/\/+$/, "").replace(/\.git$/, "");
    if (next === out) return out;
    out = next;
  }
}
