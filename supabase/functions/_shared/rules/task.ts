import type { Day } from "./day.ts";

export type TaskState = "open" | "done" | "dropped";

/**
 * A task as the rules see it, the same fields the apps' TaskItem has. `plannedTime` is `HH:MM`;
 * `createdAt` and `completedAt` are server timestamps in one fixed format, so text order is time order.
 */
export interface TaskItem {
  id: string;
  title: string;
  state: TaskState;
  topPriority: boolean;
  createdAt: string;
  plannedDate: Day | null;
  plannedTime: string | null;
  areaId: string | null;
  recurrence: string | null;
  deleted: boolean;
  seriesId: string | null;
  notes: string;
  deadline: Day | null;
  completedAt: string | null;
  /** The goal the task serves (docs/goals.md); left out where it doesn't matter. */
  goalId?: string | null;
  /** How often the task was moved from one planned day to another (docs/reviews.md). */
  movedCount?: number;
}

/** Text order, the way Kotlin and C# compare strings (UTF-16 code units). */
export function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Earlier planned times first, tasks without a time last; then by creation and id. */
export function byTime(a: TaskItem, b: TaskItem): number {
  if (a.plannedTime !== b.plannedTime) {
    if (a.plannedTime === null) return 1;
    if (b.plannedTime === null) return -1;
    return compareText(a.plannedTime, b.plannedTime);
  }
  return compareText(a.createdAt, b.createdAt) || compareText(a.id, b.id);
}

/** Oldest first, then by id. */
export function byCreation(a: TaskItem, b: TaskItem): number {
  return compareText(a.createdAt, b.createdAt) || compareText(a.id, b.id);
}
