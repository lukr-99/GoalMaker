import { addDays, type Day } from "./day.ts";
import { byTime, compareText, type TaskItem } from "./task.ts";

/** The most top priorities the ritual lets tomorrow have (docs/plan-tomorrow.md). */
export const MAX_PRIORITIES = 3;

export type PlanDecision = "undecided" | "tomorrow" | "later" | "unplanned" | "done" | "dropped";

const oldestFirst = (a: TaskItem, b: TaskItem) => compareText(a.plannedDate ?? "", b.plannedDate ?? "") || byTime(a, b);

/** Step 1: open tasks planned for today or earlier, oldest day first (contracts/vectors/plan.json). */
export function review(tasks: TaskItem[], today: Day): TaskItem[] {
  return tasks
    .filter((task) => !task.deleted && task.state === "open" && task.plannedDate !== null && task.plannedDate <= today)
    .sort(oldestFirst);
}

/** What the ritual shows for a task, read from its state so changes from elsewhere show up. */
export function decision(task: TaskItem, today: Day): PlanDecision {
  const day = task.plannedDate;
  if (task.state === "done") return "done";
  if (task.state === "dropped") return "dropped";
  if (day === null) return "unplanned";
  if (day <= today) return "undecided";
  return day === addDays(today, 1) ? "tomorrow" : "later";
}

/** Step 2: tomorrow's open tasks by time. */
export function tomorrow(tasks: TaskItem[], today: Day): TaskItem[] {
  const next = addDays(today, 1);
  return tasks.filter((task) => !task.deleted && task.state === "open" && task.plannedDate === next).sort(byTime);
}

/** How many of tomorrow's open tasks are top priorities. */
export function priorities(tasks: TaskItem[], today: Day): number {
  const next = addDays(today, 1);
  return tasks.filter((task) => !task.deleted && task.state === "open" && task.topPriority && task.plannedDate === next)
    .length;
}
