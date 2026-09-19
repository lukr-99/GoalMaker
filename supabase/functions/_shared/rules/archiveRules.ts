import { compareText, type TaskItem } from "./task.ts";

/**
 * The done tasks that match `query`, the most recently completed first (ties by id), as in
 * docs/archive.md and contracts/vectors/archive.json. Every word of the query has to be found in the
 * title or the notes, ignoring case and accents.
 */
export function searchArchive(tasks: TaskItem[], query: string): TaskItem[] {
  const words = query.split(" ").map(fold).filter((word) => word.length > 0);
  return tasks
    .filter((task) => !task.deleted && task.state === "done")
    .filter((task) => {
      const text = `${fold(task.title)}\n${fold(task.notes)}`;
      return words.every((word) => text.includes(word));
    })
    .sort((a, b) => newestFirst(a.completedAt, b.completedAt) || compareText(a.id, b.id));
}

/** Text as the search compares it: without accents, in lower case. */
export function fold(text: string): string {
  return text.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase();
}

// Newer completions first; a done task without a completion time comes last.
function newestFirst(a: string | null, b: string | null): number {
  const left = instant(a);
  const right = instant(b);
  if (left === right) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return right - left;
}

/** Microseconds since the epoch of a server timestamp, keeping the digits a JavaScript Date drops. */
export function instant(text: string | null): number | null {
  if (text === null) return null;
  const match = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?(Z|[+-]\d{2}:?\d{2})$/.exec(text);
  if (!match) return null;
  const seconds = Date.parse(`${match[1]}${match[3]}`);
  return Number.isNaN(seconds) ? null : seconds * 1000 + Number((match[2] ?? "").padEnd(6, "0"));
}
