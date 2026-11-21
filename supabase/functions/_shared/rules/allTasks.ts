import { fold } from "./archiveRules.ts";
import { compareText, type TaskItem } from "./task.ts";

/** One area's tasks; `areaId` null is the group of tasks without an area, which comes last. */
export interface TaskGroup {
  areaId: string | null;
  tasks: TaskItem[];
}

/** The words of a search, folded the way `matches` compares them. */
export function searchWords(query: string): string[] {
  return query.split(" ").map(fold).filter((word) => word !== "");
}

/** Whether every one of `words` is in the task's title or notes, ignoring case and accents. */
export function matches(task: TaskItem, words: string[]): boolean {
  if (words.length === 0) return true;
  const text = `${fold(task.title)}\n${fold(task.notes)}`;
  return words.every((word) => text.includes(word));
}

/** By date (the deadline, else the planned day), earliest first and undated last, then title, then id. */
export function byDate(a: TaskItem, b: TaskItem): number {
  const [x, y] = [a.deadline ?? a.plannedDate, b.deadline ?? b.plannedDate];
  if (x !== y) {
    if (x === null) return 1;
    if (y === null) return -1;
    return compareText(x, y);
  }
  return compareText(fold(a.title), fold(b.title)) || compareText(a.id, b.id);
}

/**
 * The All tasks place (docs/all-tasks.md, contracts/vectors/all-tasks.json): the open tasks that match
 * `query`, grouped by area in the order of `areaIds`, the no-area group last and empty groups left out.
 * A project item shows only with `projectItems`; a task's group is its own area, else its project's,
 * and an area not in `areaIds` counts as none.
 */
export function taskGroups(
  tasks: TaskItem[],
  areaIds: string[],
  projectAreas: Map<string, string | null>,
  query: string,
  projectItems: boolean,
): TaskGroup[] {
  const words = searchWords(query);
  const known = new Set(areaIds);
  const groupOf = (task: TaskItem) => {
    const area = task.areaId ?? (task.projectId ? projectAreas.get(task.projectId) ?? null : null);
    return area !== null && known.has(area) ? area : null;
  };
  const shown = tasks.filter((task) =>
    !task.deleted && task.state === "open" && (projectItems || !task.projectId) && matches(task, words)
  );
  return [...areaIds, null]
    .map((areaId) => ({ areaId, tasks: shown.filter((task) => groupOf(task) === areaId).sort(byDate) }))
    .filter((group) => group.tasks.length > 0);
}
