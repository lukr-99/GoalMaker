import { nameBasedUuid } from "./nameBasedUuid.ts";
import { compareText, type TaskItem } from "./task.ts";

/**
 * How a repeating task's series moves on across devices (docs/repeating.md,
 * contracts/vectors/recurrence.json): the ids the next occurrence and its tag links get, and which
 * open occurrences to drop when a sync left a series with more than one.
 */
export const SUCCESSOR_NAMESPACE = "77797aa7-9e11-42d8-a667-24b0596d2a4f";

/** The next occurrence's id: the same on every device that moves this occurrence on. */
export function successorId(id: string): Promise<string> {
  return nameBasedUuid(SUCCESSOR_NAMESPACE, id.toLowerCase());
}

/** The id of a next occurrence's link to a tag. */
export function tagLinkId(taskId: string, tagId: string): Promise<string> {
  return nameBasedUuid(SUCCESSOR_NAMESPACE, `${taskId.toLowerCase()}/${tagId.toLowerCase()}`);
}

/** The series a task belongs to: its series id, or its own id when it has none. */
export function seriesOf(task: TaskItem): string {
  return task.seriesId ?? task.id;
}

/** The open occurrences to drop so each series keeps one: the one planned latest (ties: the larger id). */
export function toDrop(tasks: TaskItem[]): string[] {
  const series = new Map<string, TaskItem[]>();
  for (const task of tasks.filter((task) => !task.deleted && task.state === "open")) {
    const key = seriesOf(task);
    series.set(key, [...(series.get(key) ?? []), task]);
  }
  return [...series.values()]
    .filter((occurrences) => occurrences.length > 1)
    .flatMap((occurrences) =>
      occurrences
        .sort((a, b) => latestFirst(a.plannedDate, b.plannedDate) || compareText(b.id, a.id))
        .slice(1)
    )
    .map((task) => task.id);
}

// Later days first; an occurrence without a day comes after every planned one.
function latestFirst(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return compareText(b, a);
}
