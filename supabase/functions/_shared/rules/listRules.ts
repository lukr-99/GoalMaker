import { addDays, type Day } from "./day.ts";
import { byCreation, byTime, compareText, type TaskItem } from "./task.ts";

export interface TodaySections {
  priorities: TaskItem[];
  scheduled: TaskItem[];
  more: TaskItem[];
  overdue: TaskItem[];
}

/** Every list the planner shows, computed together from the same tasks and planning day. */
export interface PlanningLists {
  today: Day;
  todaySections: TodaySections;
  tomorrow: TaskItem[];
  inbox: TaskItem[];
  summary: { done: number; total: number };
}

/** Which open tasks each list shows, in what order (docs/lists.md, contracts/vectors/lists.json). */
export function lists(tasks: TaskItem[], today: Day): PlanningLists {
  const live = tasks.filter((task) => !task.deleted);
  const open = live.filter((task) => task.state === "open");
  const planned = open.filter((task) => task.plannedDate === today);
  const tomorrow = addDays(today, 1);
  const counted = live.filter((task) => task.plannedDate === today && task.state !== "dropped");
  return {
    today,
    todaySections: {
      priorities: planned.filter((task) => task.topPriority).sort(byTime),
      scheduled: planned.filter((task) => !task.topPriority && task.plannedTime !== null).sort(byTime),
      more: planned.filter((task) => !task.topPriority && task.plannedTime === null).sort(byCreation),
      overdue: open
        .filter((task) => task.plannedDate !== null && task.plannedDate < today)
        .sort((a, b) => compareText(a.plannedDate!, b.plannedDate!) || byTime(a, b)),
    },
    tomorrow: open
      .filter((task) => task.plannedDate === tomorrow)
      .sort((a, b) => Number(b.topPriority) - Number(a.topPriority) || byTime(a, b)),
    inbox: open.filter((task) => task.plannedDate === null && task.areaId === null && (task.projectId ?? null) === null)
      .sort(byCreation),
    summary: {
      done: counted.filter((task) => task.state === "done").length,
      total: counted.length,
    },
  };
}
