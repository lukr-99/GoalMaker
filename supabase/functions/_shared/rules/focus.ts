import { fold } from "./archiveRules.ts";
import { compareText, type TaskItem } from "./task.ts";

/**
 * The Focus Board (supabase/migrations/0029_focus_board.sql): the open tasks with a focus position, in
 * the owner's order. The apps' FocusBoardRules do the same (contracts/vectors/focus.json).
 */
export function focusBoard(tasks: TaskItem[]): TaskItem[] {
  return tasks
    .filter((task) => !task.deleted && task.state === "open" && task.focusPosition != null)
    .sort((a, b) =>
      a.focusPosition! - b.focusPosition! || compareText(a.createdAt, b.createdAt) || compareText(a.id, b.id)
    );
}

/** The position a task added at the end gets: one more than the last one's, or 1 on an empty board. */
export function appendPosition(tasks: TaskItem[]): number {
  return (focusBoard(tasks).at(-1)?.focusPosition ?? 0) + 1;
}

/** A position between two neighbours, either of which can be missing (the top or the end). */
export function between(before: number | null, after: number | null): number {
  if (before === null && after === null) return 1;
  if (before === null) return after! - 1;
  if (after === null) return before + 1;
  return (before + after) / 2;
}

/**
 * The position that puts `taskId` at `place` on the board, 1 being the top, with the task itself left
 * out first. A place past the end is the end, and one below 1 is the top.
 */
export function placeAt(tasks: TaskItem[], taskId: string, place: number): number {
  const others = focusBoard(tasks).filter((task) => task.id !== taskId);
  const index = Math.min(Math.max(Math.trunc(place) - 1, 0), others.length);
  return between(others[index - 1]?.focusPosition ?? null, others[index]?.focusPosition ?? null);
}

/**
 * The tasks the apps' add picker offers for `query`: open, not deleted and not on the board, with every
 * word of the query in the title or the notes, ignoring case and accents. By their date (the deadline,
 * else the planned day), earliest first and undated last, then by title, then by id.
 */
export function focusCandidates(tasks: TaskItem[], query: string): TaskItem[] {
  const words = query.split(" ").map(fold).filter((word) => word !== "");
  const dateOf = (task: TaskItem) => task.deadline ?? task.plannedDate;
  return tasks
    .filter((task) => !task.deleted && task.state === "open" && task.focusPosition == null)
    .filter((task) => {
      const text = `${fold(task.title)}\n${fold(task.notes)}`;
      return words.every((word) => text.includes(word));
    })
    .sort((a, b) => {
      const [x, y] = [dateOf(a), dateOf(b)];
      if (x !== y) {
        if (x === null) return 1;
        if (y === null) return -1;
        return compareText(x, y);
      }
      return compareText(fold(a.title), fold(b.title)) || compareText(a.id, b.id);
    });
}
