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
  /** When it was logged, so the quick log can repeat the latest. */
  createdAt?: string;
}

/** Where a goal stands against the share of its period gone by, in the order a rung sorts by. */
export type GoalPace = "behind" | "on_track" | "hit" | "dropped";

export interface GoalStanding {
  pace: GoalPace;
  /** What a goal counted by tasks or a number that is behind is missing, rounded up. */
  behind: number | null;
}

const PACE_ORDER: Record<GoalPace, number> = { behind: 0, on_track: 1, hit: 2, dropped: 3 };

/** How far under the share of its period gone by a goal can be and still be on track. */
export const PACE_SLACK = 0.05;

/** The share of its period gone by after which an open done-or-not goal needs you. */
export const DUE_SOON = 0.7;

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

/** The share of the `horizon` period starting on `start` gone by before `today`: 0 before it starts, 1 after it ends. */
export function elapsed(horizon: GoalHorizon, start: Day, today: Day): number {
  const length = toEpochDay(periodEnd(horizon, start)) - toEpochDay(start) + 1;
  const gone = Math.min(length, Math.max(0, toEpochDay(today) - toEpochDay(start)));
  return gone / length;
}

/**
 * Where `goal` stands on `today` with its `progress`: dropped, hit, behind or on track. A goal counted
 * by tasks or a number is on track while its fraction is within PACE_SLACK of the share of its period
 * gone by, and otherwise behind by what is missing to it, rounded up. A done-or-not goal, or one with
 * nothing to count, is behind once DUE_SOON of its period is gone.
 */
export function goalStanding(
  goal: Pick<GoalItem, "horizon" | "periodStart" | "mode" | "status">,
  progress: GoalProgress,
  today: Day,
): GoalStanding {
  if (goal.status === "dropped") return { pace: "dropped", behind: null };
  if (progress.hit) return { pace: "hit", behind: null };
  const gone = elapsed(goal.horizon, goal.periodStart, today);
  if (goal.mode === "done" || progress.target <= 0) {
    return { pace: gone >= DUE_SOON ? "behind" : "on_track", behind: null };
  }
  if (progress.fraction + PACE_SLACK >= gone) return { pace: "on_track", behind: null };
  return { pace: "behind", behind: Math.ceil(gone * progress.target - progress.value - 1e-9) };
}

/** `items` for a rung: the ones behind first, then on track, hit and dropped, each in its own order. */
export function byPace<T>(items: T[], pace: (item: T) => GoalPace): T[] {
  return [...items].sort((a, b) => PACE_ORDER[pace(a)] - PACE_ORDER[pace(b)]);
}

/**
 * What lights up when the goal `id` is picked: it, every goal it feeds up the cascade and every goal
 * that feeds it, however deep. Empty when `goals` has no such goal.
 */
export function goalChain(goals: Pick<GoalItem, "id" | "parentId">[], id: string): Set<string> {
  const byId = new Map(goals.map((goal) => [goal.id, goal]));
  const lit = new Set<string>();
  if (!byId.has(id)) return lit;
  lit.add(id);
  let up = byId.get(id)!.parentId;
  while (up !== null && byId.has(up) && !lit.has(up)) {
    lit.add(up);
    up = byId.get(up)!.parentId;
  }
  const seen = new Set([id]);
  const down = [id];
  while (down.length > 0) {
    const parent = down.shift()!;
    for (const child of goals) {
      if (child.parentId === parent && !seen.has(child.id)) {
        seen.add(child.id);
        lit.add(child.id);
        down.push(child.id);
      }
    }
  }
  return lit;
}

/** What the quick log adds on a numeric goal: the latest positive amount logged by hand, or null. */
export function quickAmount(entries: GoalEntryItem[]): number | null {
  let latest: GoalEntryItem | null = null;
  for (const entry of entries) {
    if (entry.deleted || entry.amount <= 0) continue;
    if (latest === null || (entry.createdAt ?? "") > (latest.createdAt ?? "")) latest = entry;
  }
  return latest?.amount ?? null;
}
