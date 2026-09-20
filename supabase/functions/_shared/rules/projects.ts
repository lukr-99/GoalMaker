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
