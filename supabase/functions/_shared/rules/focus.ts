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
