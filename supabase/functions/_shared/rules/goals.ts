import { addDays, type Day, fromEpochDay, mondayOf, toEpochDay } from "./day.ts";
import type { TaskItem } from "./task.ts";

/** Goal periods, the cascade and progress (docs/goals.md, contracts/vectors/goals.json). */
export type GoalHorizon = "year" | "month" | "week" | "day";
export type GoalMode = "done" | "tasks" | "number";
export type GoalStatus = "open" | "done" | "dropped";

const RANK: Record<GoalHorizon, number> = { year: 3, month: 2, week: 1, day: 0 };

export interface GoalItem {
  id: string;
  title: string;
  horizon: GoalHorizon;
  periodStart: Day;
  mode: GoalMode;
  status: GoalStatus;
  emoji: string | null;
  parentId: string | null;
  target: number | null;
  unit: string | null;
  deleted: boolean;
}

export interface GoalEntryItem {
  amount: number;
  deleted: boolean;
}

export interface GoalProgress {
  value: number;
  target: number;
  fraction: number;
  hit: boolean;
}

export interface GoalCopy {
  title: string;
  emoji: string | null;
  mode: GoalMode;
  target: number | null;
  unit: string | null;
  parentId: string | null;
}

/** The first day of the `horizon` period `day` falls in (weeks start on Monday). */
export function periodStart(horizon: GoalHorizon, day: Day): Day {
  switch (horizon) {
    case "year":
      return `${day.slice(0, 4)}-01-01`;
    case "month":
      return `${day.slice(0, 7)}-01`;
    case "week":
      return mondayOf(day);
    case "day":
      return day;
  }
}

/** The last day of the `horizon` period starting on `start`. */
export function periodEnd(horizon: GoalHorizon, start: Day): Day {
  switch (horizon) {
    case "year":
      return `${start.slice(0, 4)}-12-31`;
    case "month": {
      const next = new Date(Date.UTC(Number(start.slice(0, 4)), Number(start.slice(5, 7)), 1));
      return fromEpochDay(toEpochDay(next.toISOString().slice(0, 10)) - 1);
    }
    case "week":
      return addDays(start, 6);
    case "day":
      return start;
  }
}

/** Whether a goal of `parent` can be the parent of a goal of `child`: a longer horizon whose period overlaps. */
export function canServe(child: GoalHorizon, childStart: Day, parent: GoalHorizon, parentStart: Day): boolean {
  return RANK[parent] > RANK[child] && parentStart <= periodEnd(child, childStart) &&
    childStart <= periodEnd(parent, parentStart);
}

/** Where a goal stands, from the tasks that serve it and the entries logged on it. */
export function goalProgress(
  mode: GoalMode,
  status: GoalStatus,
  target: number | null,
  tasks: TaskItem[],
  entries: GoalEntryItem[],
): GoalProgress {
  let value: number;
  let goal: number;
  if (mode === "tasks") {
    const counted = tasks.filter((task) => !task.deleted && task.state !== "dropped");
    value = counted.filter((task) => task.state === "done").length;
    goal = counted.length;
  } else if (mode === "number") {
    value = entries.filter((entry) => !entry.deleted).reduce((sum, entry) => sum + entry.amount, 0);
    goal = target ?? 0;
  } else {
    value = status === "done" ? 1 : 0;
    goal = 1;
  }
  const fraction = status === "done" ? 1 : goal <= 0 ? 0 : Math.min(1, Math.max(0, value / goal));
  const hit = status === "done" || (status !== "dropped" && goal > 0 && value >= goal);
  return { value, target: goal, fraction, hit };
}

/**
 * Last period's goals as copies for a new `horizon` period starting on `start`: all but the dropped,
 * each keeping its parent only when that goal still overlaps the period.
 */
export function goalCopies(
  goals: GoalItem[],
  parents: Map<string, GoalItem>,
  horizon: GoalHorizon,
  start: Day,
): GoalCopy[] {
  return goals.filter((goal) => !goal.deleted && goal.status !== "dropped").map((goal) => {
    const parent = goal.parentId === null ? undefined : parents.get(goal.parentId);
    const kept = parent !== undefined && canServe(horizon, start, parent.horizon, parent.periodStart);
    return {
      title: goal.title,
      emoji: goal.emoji,
      mode: goal.mode,
      target: goal.target,
      unit: goal.unit,
      parentId: kept ? parent!.id : null,
    };
  });
}
